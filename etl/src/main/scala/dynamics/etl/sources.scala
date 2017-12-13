// Copyright (c) 2017 aappddeevv@gmail.com
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package etl

import scala.scalajs.js
import js._
import js.Dynamic.{literal => jsobj}
import js.annotation._
import JSConverters._
import scala.concurrent._
import io.scalajs.util.PromiseHelper.Implicits._
import fs2._
import fs2.util._
import cats._
import cats.data._
import cats.implicits._
import fs2.interop.cats._
import scala.scalajs.runtime.wrapJavaScriptException

import io.scalajs.RawOptions
import io.scalajs.nodejs.Error
import io.scalajs.nodejs
import io.scalajs.nodejs.buffer.Buffer
import io.scalajs.nodejs.stream.{Readable, Writable}
import io.scalajs.nodejs.events.IEventEmitter
import io.scalajs.util.PromiseHelper.Implicits._
import io.scalajs.nodejs.fs._
import io.scalajs.npm.csvparse._

import dynamics.common._
import fs2helpers._
import dynamics.common.implicits._
import MonadlessTask._

object sources {

  import fs2.Task._

  /**
    * Turn a Readable parser into a Stream of js.Object using
    * the callback `onData`. While `A` could be a Buffer or String,
    * it could also be a js.Object. `A` must reflect the callers
    * understanding of what objects the Readable will produce. You could use
    * this over readable.iterator => fs2.Stream when you want to bubble
    * up errors explicitly through the fs2 layer.
    */
  def readableToStream[A](readable: io.scalajs.nodejs.stream.Readable, qsize: Int = 1000)(
      implicit s: Strategy): Stream[Task, A] = {
    val counter = new java.util.concurrent.atomic.AtomicInteger(-1)
    //val F = Task.asyncInstance(Strategy.sequential) // mpilquist suggested sync enqueue1
    for {
      q <- Stream.eval(fs2.async.boundedQueue[Task, Option[Either[Throwable, A]]](qsize))
      _ <- Stream.eval(Task.delay {
        readable.onData { (data: A) =>
          val x = counter.getAndIncrement()
          q.enqueue1(Some(Right(data))).unsafeRunAsync {
            _ match {
              case Right(v) => () //println(s"Readable: $x: $data added to queue.")
              case Left(t)  => println(s"Error adding to queue, index ($x): $t")
            }
          }
        }
        readable.onError { (e: io.scalajs.nodejs.Error) =>
          q.enqueue1(Some(Left(wrapJavaScriptException(e)))).unsafeRunAsync(_ => ())
        }
        readable.onEnd { () =>
          q.enqueue1(None).unsafeRunAsync(_ => ())
        }
        readable.onClose { () =>
          q.enqueue1(None).unsafeRunAsync(_ => ())
        }
      }) // could use .attempt here but need to wrap js exception inside :-(
      record <- q.dequeue.unNoneTerminate through pipe.rethrow
    } yield record
  }

  /**
    * Stream a file containing JSON objects separated by newlines. Newlines should be
    * escaped inside JSON values. isArray = true implies that json objects inside
    * are wrapped in an array and hence have commas separating the objects.
    */
  def JSONFileSource[A](file: String, isArray: Boolean = false)(implicit s: Strategy): Stream[Task, A] = {
    Stream.bracket(Task.delay {
      val source = if (!isArray) StreamJsonObjects.make() else StreamArray.make()
      (source, Fs.createReadStream(file))
    })(
      p => { p._2.pipe(p._1.input); readableToStream[A](p._1.output) },
      p => Task.delay(()) // rely on autoclose behavior
    )
  }

  /** does not appear to work at all... */
  private[dynamics] def JSONFileSource2[A](file: String)(implicit s: Strategy,
                                                         ec: ExecutionContext): Stream[Task, A] = {
    val source = StreamJsonObjects.make()
    val x      = Fs.createReadStream(file)
    x.pipe(source.input)
    IteratorOps(source.output.iterator).toFS2Stream
  }

  val DefaultCSVParserOptions =
    new ParserOptions(columns = true, skip_empty_lines = true, trim = true, auto_parse = false)

  /**
    * Create a Stream[Task, js.Object] from a
    * file name and CSV options using csv-parse. Run `start` to start the OS reading.
    * The underlying CSV file is automatically opened and closed.
    */
  def CSVFileSource(file: String, csvParserOptions: ParserOptions)(implicit s: Strategy): Stream[Task, js.Object] = {
    CSVFileSource_(file, csvParserOptions, readableToStream[js.Object](_: io.scalajs.nodejs.stream.Readable))
  }

  def CSVFileSource_(file: String,
                     csvParserOptions: ParserOptions,
                     f: io.scalajs.nodejs.stream.Readable => Stream[Task, js.Object])(
      implicit s: Strategy): Stream[Task, js.Object] = {
    val create = Task.delay {
      val parser = CsvParse(csvParserOptions)
      val f      = Fs.createReadStream(file)
      (f, parser)
    }
    Stream.bracket(create)(
      {
        case (rstr, parser) =>
          val rval = f(parser) // readable -> fs2 Stream
          rstr.pipe(parser) // pipe start parsing, makes it "flow" modee
          rval
      }, {
        case (rstr, parser) =>
          Task.delay {
            rstr.unpipe()
            rstr.close(_ => ())
          }
      }
    )
  }

  def CSVFileSourceBuffer_(file: String, csvParserOptions: ParserOptions = DefaultCSVParserOptions)(
      implicit e: ExecutionContext,
      s: Strategy): Task[scala.Iterable[js.Object]] = {
    import scala.scalajs.runtime.wrapJavaScriptException
    val p = scala.concurrent.Promise[Seq[js.Object]]()
    import collection.mutable.ListBuffer
    val result           = ListBuffer[js.Object]()
    val parser: Readable = CsvParse(csvParserOptions)
    val f                = Fs.createReadStream(file)
    parser.onData { (data: js.Object) =>
      result += data
    }
    parser.onEnd { () =>
      f.unpipe()
      f.close(_ => ())
      p.success(result.seq)
    }
    parser.onError { (e: io.scalajs.nodejs.Error) =>
      p.failure(wrapJavaScriptException(e))
    }
    f.pipe(parser.asInstanceOf[Writable])
    p.future.toTask
  }

  /**
    * Assuming the query has been set to streaming, stream the results.
    */
  def queryToStream[A](query: Query, qsize: Int = 1000)(implicit s: Strategy): Stream[Task, A] = {
    val F = Async[Task]
    for {
      q <- Stream.eval(fs2.async.boundedQueue[Task, Option[Either[Throwable, A]]](qsize))
      _ <- Stream.eval(Task.delay {
        query.on("row", (data: A) => q.enqueue1(Some(Right(data))).unsafeRunAsync(_ => ()))
        query.on(
          "error",
          (e: io.scalajs.nodejs.Error) => q.enqueue1(Some(Left(wrapJavaScriptException(e)))).unsafeRunAsync(_ => ()))
        query.on("end", (_: js.Any) => q.enqueue1(None).unsafeRunAsync(_ => ()))
      })
      a <- q.dequeue.unNoneTerminate through pipe.rethrow
    } yield a
  }

  /**
    * A MSSQL source that executes a query. If you create your own coonnection
    * pool then use `queryToStream` once you create your query request.
    * @see https://www.npmjs.com/package/mssql#tedious connection string info.
    */
  def MSSQLSource[A](qstr: String, config: RawOptions | String)(implicit s: Strategy): Stream[Task, A] = {
    def create() = MSSQL.connect(config).toTask.map { pool =>
      val query = pool.request()
      query.stream = true
      query.query(qstr)
      query
    }
    Stream.bracket(create())(
      (q: Query) => queryToStream(q),
      (q: Query) => Task.now(()) // close something?
    )
  }

  /** Add a source string based on the record index position.
    */
  def toInput[A](startIndex: Int = 1): Pipe[Task, A, InputContext[A]] =
    _.zipWithIndex.map {
      case (rec, idx) =>
        val i = idx + startIndex
        InputContext[A](rec, s"Record $i")
    }

}
