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

class MSSQLConfig(
    val user: js.UndefOr[String] = js.undefined,
    val password: js.UndefOr[String] = js.undefined,
    val server: js.UndefOr[String] = js.undefined,
    val database: js.UndefOr[String] = js.undefined,
    val port: js.UndefOr[Int] = js.undefined,
    val domain: js.UndefOr[String] = js.undefined,
    val connectionTimeout: js.UndefOr[Int] = js.undefined,
    val requestTimeout: js.UndefOr[Int] = js.undefined,
    val parseJSON: js.UndefOr[Boolean] = js.undefined,
    val stream: js.UndefOr[Boolean] = js.undefined,
    val pool: js.UndefOr[PoolOptions] = js.undefined,
    val options: js.UndefOr[RawOptions] = js.undefined
) extends js.Object

class PoolOptions(
    val max: js.UndefOr[Int] = js.undefined,
    val min: js.UndefOr[Int] = js.undefined,
    val idleTimeoutMillis: js.UndefOr[Int] = js.undefined,
    val acquireTimeoutMillis: js.UndefOr[Int] = js.undefined,
    val fifo: js.UndefOr[Boolean] = js.undefined,
    val priorityRange: js.UndefOr[Int] = js.undefined,
    val autostart: js.UndefOr[Boolean] = js.undefined
    // ...more should go here...
) extends js.Object

@js.native
@JSImport("mssql", JSImport.Namespace)
object MSSQL extends js.Object {
  def connect(config: RawOptions): js.Promise[Pool] = js.native
}

@js.native
trait Pool extends js.Object {
  def request(): Query = js.native
}

@js.native
trait Query extends js.Object with IEventEmitter {
  def input(p: String, t: Int, value: js.Any): Query = js.native
  var stream: js.UndefOr[Boolean]                    = js.native
  def query(q: String): js.Object                    = js.native
}

object sources {

  import fs2.Task._

  /**
    * Turn a Readable parser into a Stream of js.Object using
    * the callback `onData`. While `A` could be a Buffer or String,
    * it could also be a js.Object. `A` must reflect the callers
    * understanding of what objects the Readable will produce.
    */
  def readableToStream[A](readable: Readable, qsize: Int = 1000)(implicit s: Strategy): Stream[Task, A] = {
    val counter = new java.util.concurrent.atomic.AtomicInteger(1)
    import scala.scalajs.runtime.wrapJavaScriptException
    val F = Task.asyncInstance(Strategy.sequential) // mpilquist suggested sync enqueue1
    for {
      q <- Stream.eval(fs2.async.boundedQueue[Task, Option[Either[Throwable, A]]](qsize))
      _ <- Stream.eval(Task.delay {
        readable.onData {
          (data: A) =>
            val x = counter.getAndIncrement()
            //println(s"Readable: $x: $data") // immediate print when a data event occurs.
            // Try 1 - run sync by using a sequential F above...
            //F.unsafeRunAsync(q.enqueue1(Some(Right(data))))(_ => ()) // change to unsafeRunSync?
            // Try 2 - pure async
            q.enqueue1(Some(Right(data))).unsafeRunAsync {
              _ match {
                case Right(v) => (); //println(s"Readable: $x: $data added to queue.")
                case Left(t)  => println(s"Error adding to queue ($x): $t")
              }
            }
          // Try 3 - looks like it ALWAYS hits an async boundary so "cb" is always called
          // q.enqueue1(Some(Right(data))).unsafeRunSync() match {
          //   case Right(v) => println(s"Readable: $x: $data added to queue.")
          //   case Left(cb) => //println(s"cb: $x")
          //      cb{ _ match {
          //        case Right(v) => println(s"Hit async boundary: $x")
          //        case Left(t) => println(s"Error inserting into queue: $x")
          //     }}
          // }
        }
        readable.onError { (e: io.scalajs.nodejs.Error) =>
          q.enqueue1(Some(Left(wrapJavaScriptException(e)))).unsafeRunAsync(_ => ())
        }
        readable.onEnd { () =>
          println("READABLE COMPLETE (end)")
          q.enqueue1(None).unsafeRunAsync(_ => ())
        }
        readable.onClose { () =>
          println("READABLE COMPLETE (close)")
          q.enqueue1(None).unsafeRunAsync(_ => ())
        }
        //Stream.emit(()) //only needed if using Stream.suspend...
      }) // could use .attempt here but need to wrap js exception inside :-(
      record <- q.dequeue.unNoneTerminate through pipe.rethrow
    } yield record
  }

  val DefaultCSVParserOptions =
    new ParserOptions(columns = true, skip_empty_lines = true, trim = true, auto_parse = false)

  /**
    * Create a Stream[Task, js.Object] from a
    * file name and CSV options using csv-parse. Run `start` to start the OS reading.
    * The underlying CSV file is automatically opened and closed.
    */
  def CSVFileSource(file: String, csvParserOptions: ParserOptions)(implicit s: Strategy): Stream[Task, js.Object] = {
    CSVFileSource_(file, csvParserOptions, readableToStream[js.Object](_: Readable))
  }

  def CSVFileSource_(file: String, csvParserOptions: ParserOptions, f: Readable => Stream[Task, js.Object])(
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
      s: Strategy): Task[Seq[js.Object]] = {
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

  def queryToStream[A](query: Query, qsize: Int = 100)(implicit s: Strategy): Stream[Task, A] = {
    val F = Async[Task]
    for {
      q <- Stream.eval(fs2.async.boundedQueue[Task, A](qsize))
      _ <- Stream.suspend {
        query.on("row", { (data: A) =>
          F.unsafeRunAsync(q.enqueue1(data))(_ => ())
        })
        query.on("error", { (err: js.Error) =>
          println(s"Error in MSSQL query processing: ${PrettyJson.render(err)}")
          throw new JavaScriptException(err)
        })
        Stream.emit(())
      }
      a <- q.dequeue // returns stream that dequeues forever
    } yield a
  }

  /**
    * A MSSQL source that executes a query.
    * @see https://patriksimek.github.io/node-mssql/#promises
    */
  def MSSQLSource[A](qstr: String, config: RawOptions)(implicit s: Strategy): Stream[Task, A] = {
    def create() = MSSQL.connect(config).toTask.map { pool =>
      val query = pool.request()
      query.stream = true
      query.query(qstr)
      query
    }
    Stream.bracket(create())({ q: Query =>
      queryToStream(q)
    }, { q: Query =>
      Task.now(())
    })
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
