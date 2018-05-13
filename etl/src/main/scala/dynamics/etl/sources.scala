// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package etl

import scala.scalajs.js
import js._
import js.Dynamic.{literal => jsobj}
import JSConverters._
import scala.concurrent._
import io.scalajs.util.PromiseHelper.Implicits._
import fs2._
import cats._
import cats.data._
import cats.implicits._
import cats.effect._
import scala.scalajs.runtime.wrapJavaScriptException

import io.scalajs.RawOptions
import io.scalajs.nodejs.Error
import io.scalajs.nodejs
import io.scalajs.nodejs.readline._
import io.scalajs.nodejs.buffer.Buffer
import io.scalajs.nodejs.stream.{Readable, Writable}
import io.scalajs.nodejs.events.IEventEmitter
import io.scalajs.util.PromiseHelper.Implicits._
import io.scalajs.nodejs.fs._
import io.scalajs.npm.csvparse._

import dynamics.common._
import fs2helpers._
import dynamics.common.implicits._

object sources {

  type EventRegistration[F[_], A] = QueueForStream[F, A] => js.UndefOr[A] => Unit

  /**
    * Convert a readable to a fs2 Stream given a specific set of events and
    * their handlers. Each handler can signal the end of the stream by
    * enqueuing None or an error by returning a Left. Handler return results via
    * `qparam.enqueue1(Some(Right(data))).unsafeRunAsync(_ => ())` or something
    * similar. If your callbacks all have different types, you may have
    * to type out your own conversion or cast.
    */
  def jsToFs2Stream[F[_], A](events: Seq[(String, EventRegistration[F, A])],
                             source: io.scalajs.nodejs.events.IEventEmitter,
                             qsize: Int = 1000)(implicit ec: ExecutionContext, F: Effect[F]): Stream[F, A] = {
    for {
      q      <- Stream.eval(fs2.async.boundedQueue[F, Option[Either[Throwable, A]]](qsize))
      _      <- Stream.eval(F.delay { events.foreach(p => source.on(p._1, p._2(q))) })
      record <- q.dequeue.unNoneTerminate.rethrow
    } yield record
  }

  /**
    * Turn a Readable parser into a Stream of js.Object using the callback
    * `onData`. While `A` could be a Buffer or String, it could also be a
    * js.Object. `A` must reflect the callers understanding of what objects the
    * Readable will produce. You could use this over readable.iterator =>
    * fs2.Stream when you want to bubble up errors explicitly through the fs2
    * layer. If you have other event names that are used for the callbacks, use
    * `readableToStreamWithEvents`.
    */
  def readableToStream[F[_], A](readable: io.scalajs.nodejs.stream.Readable,
                                qsize: Int = 1000)(implicit ec: ExecutionContext, F: Effect[F]): Stream[F, A] = {
    val counter = new java.util.concurrent.atomic.AtomicInteger(-1)
    for {
      q <- Stream.eval(fs2.async.boundedQueue[F, Option[Either[Throwable, A]]](qsize))
      _ <- Stream.eval(F.delay {
        readable.onError { (e: io.scalajs.nodejs.Error) =>
          async.unsafeRunAsync(q.enqueue1(Some(Left(wrapJavaScriptException(e)))))(_ => IO.unit)
        }
        readable.onEnd { () =>
          async.unsafeRunAsync(q.enqueue1(None))(_ => IO.unit)
        }
        readable.onClose { () =>
          async.unsafeRunAsync(q.enqueue1(None))(_ => IO.unit)
        }
        readable.onData { (data: A) =>
          val x = counter.getAndIncrement()
          async.unsafeRunAsync(q.enqueue1(Some(Right(data)))) {
            _ match {
              case Right(v) => IO.unit //println(s"Readable: $x: $data added to queue.")
              case Left(t)  => IO(println(s"Error adding to queue, index ($x): $t"))
            }
          }
        }
      })
      record <- q.dequeue.unNoneTerminate.rethrow
    } yield record
  }

  /**
    * Stream a file containing JSON objects separated by newlines. Newlines
    * should be escaped inside JSON values. isArray = true implies that json
    * objects inside are wrapped in an array and hence have commas separating
    * the objects.
    */
  def JSONFileSource[A](file: String, isArray: Boolean = false)(implicit ec: ExecutionContext): Stream[IO, A] = {
    Stream.bracket(IO {
      val source = if (!isArray) StreamJsonObjects.make() else StreamArray.make()
      (source, Fs.createReadStream(file))
    })(
      p => { p._2.pipe(p._1.input); readableToStream[IO, A](p._1.output) },
      p => IO(()) // rely on autoclose behavior
    )
  }

  /** does not appear to work at all... */
  private[dynamics] def JSONFileSource2[A](file: String)(implicit ec: ExecutionContext): Stream[IO, A] = {
    val source = StreamJsonObjects.make()
    val x      = Fs.createReadStream(file)
    x.pipe(source.input)
    Stream.fromIterator[IO, A](source.output.iterator[A])
  }

  val DefaultCSVParserOptions =
    new ParserOptions(columns = true, skip_empty_lines = true, trim = true, auto_parse = false)

  /**
    * Create a Stream[IO, js.Object] from a file name and CSV options using
    * csv-parse. Run `start` to start the OS reading.  The underlying CSV file
    * is automatically opened and closed.
    */
  def CSVFileSource(file: String, csvParserOptions: ParserOptions = DefaultCSVParserOptions)(
      implicit ec: ExecutionContext): Stream[IO, js.Object] = {
    CSVFileSource_(file, csvParserOptions, readableToStream[IO, js.Object](_: io.scalajs.nodejs.stream.Readable))
  }

  def CSVFileSource_(file: String,
                     csvParserOptions: ParserOptions,
                     f: io.scalajs.nodejs.stream.Readable => Stream[IO, js.Object])(
      implicit ec: ExecutionContext): Stream[IO, js.Object] = {
    val create = IO {
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
          IO {
            rstr.unpipe()
            rstr.close(_ => ())
          }
      }
    )
  }

  def CSVFileSourceBuffer_(file: String, csvParserOptions: ParserOptions = DefaultCSVParserOptions)(
      implicit e: ExecutionContext): IO[scala.Iterable[js.Object]] = {
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
    p.future.toIO
  }

  /**
    * Assuming the query has been set to streaming, stream the results.
    */
  def queryToStream[F[_], A](query: Request, qsize: Int = 10000)(implicit ec: ExecutionContext,
                                                                 F: Effect[F]): Stream[F, A] = {
    for {
      q <- Stream.eval(async.boundedQueue[F, Option[Either[Throwable, A]]](qsize))
      _ <- Stream.eval(F.delay {
        query.on("error",
                 (e: io.scalajs.nodejs.Error) =>
                   async.unsafeRunAsync(q.enqueue1(Some(Left(wrapJavaScriptException(e)))))(_ => IO.unit))
        query.on("done", (_: js.Any) => async.unsafeRunAsync(q.enqueue1(None))(_ => IO.unit))
        query.on("row", (data: A) => async.unsafeRunAsync(q.enqueue1(Some(Right(data))))(_ => IO.unit))
      })
      a <- q.dequeue.unNoneTerminate.rethrow
    } yield a
  }

  def MSSQLSourceRequest[A](qstr: String, config: js.Dynamic | RawOptions | String)(
      implicit ec: ExecutionContext): IO[etl.Request] = {
    MSSQL.connect(config).toIO.map { pool =>
      val request = pool.request()
      request.stream = true
      request.query(qstr)
      request
    }
  }

  /**
    * A MSSQL source that executes a query. If you create your own coonnection
    * pool then use `queryToStream` once you create your query request.
    * @see https://www.npmjs.com/package/mssql#tedious connection string info.
    * @todo Make non-native trait for the connection config options
    */
  def MSSQLSource[A](qstr: String, config: js.Dynamic | RawOptions | String, qsize: Int = 10000)(
      implicit ec: ExecutionContext): Stream[IO, A] = {
    def create() = MSSQL.connect(config).toIO.map { pool =>
      val request = pool.request()
      request.stream = true
      request.query(qstr)
      request
    }
    Stream.bracket(create())(
      (q: etl.Request) => queryToStream[IO, A](q, qsize),
      (q: etl.Request) => IO.pure(()) // close something?
    )
  }

  private[dynamics] type QueueForStream[F[_], A] = fs2.async.mutable.Queue[F, Option[Either[Throwable, A]]]

  /** Read a file (utf8) line by line. */
  def FileLines(file: String)(implicit ec: ExecutionContext): Stream[IO, String] = {
    Stream.bracket(IO {
      val instream  = Fs.createReadStream(file)
      val outstream = io.scalajs.nodejs.process.stdout
      Readline.createInterface(new ReadlineOptions(input = instream, output = outstream, terminal = false))
    })(
      interface => {
        val events: Seq[(String, EventRegistration[IO, String])] = Seq(
          ("line", q => data => data.foreach(d => q.enqueue1(Some(Right(d))).unsafeRunAsync(_ => ()))),
          ("close", q => data => q.enqueue1(None).unsafeRunAsync(_ => ())),
        )
        jsToFs2Stream[IO, String](events, interface)
      },
      i => IO(i.close())
    )
  }

  /**
    * Add a source string based on the record index position.
    */
  def toInput[A](startIndex: Int = 1): Pipe[IO, A, InputContext[A]] =
    _.zipWithIndex.map {
      case (rec, idx) =>
        val i = idx + startIndex
        InputContext[A](rec, s"Record $i")
    }

}
