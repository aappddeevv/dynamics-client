// Copyright (c) 2017 aappddeevv@gmail.com
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package common

import scala.scalajs._
import js._
import annotation._
import scala.concurrent._

//
// https://github.com/paulmillr/chokidar
//
@js.native
@JSImport("chokidar", JSImport.Namespace)
object chokidar extends js.Object {
  def watch(path: String | js.Array[String],
            opts: UndefOr[ChokidarOptions] = js.undefined): io.scalajs.nodejs.fs.FSWatcher = js.native
}

class ChokidarOptions(
    val persistent: UndefOr[Boolean] = true,
    val ignored: UndefOr[String] = js.undefined,
    val ignoreInitial: UndefOr[Boolean] = js.undefined,
    val followSymlinks: UndefOr[Boolean] = js.undefined,
    val cwd: UndefOr[String] = js.undefined,
    val disableGlobbing: UndefOr[Boolean] = js.undefined,
    val usePolling: UndefOr[Boolean] = js.undefined,
    val interval: UndefOr[Int] = js.undefined,
    val binaryInterval: UndefOr[Int] = js.undefined,
    val alwaysStat: UndefOr[Boolean] = js.undefined,
    val depth: UndefOr[Int] = js.undefined,
    val awaitWriteFinish: UndefOr[Boolean | js.Dynamic] = js.undefined,
    /*
  awaitWriteFinish: {
    stabilityThreshold: 2000,
    pollInterval: 100
  },
     */
    val ignorePermissionErrors: UndefOr[Boolean] = js.undefined,
    val atomic: UndefOr[Boolean] = js.undefined
) extends js.Object

object ChokidarOptions {
  val Empty = new ChokidarOptions()
}

@js.native
trait FSWatcher extends js.Object {
//  def add(path: String|Array[String], js.Function1[String, Unit]): FSWatcher = js.native
  def on(event: String, cb: js.Function1[String, Unit]): FSWatcher = js.native

  /** Only for add, addDir, change. */
  @JSName("on")
  def onWithStats(event: String, cb: js.Function2[String, js.UndefOr[js.Dynamic], Unit]): FSWatcher = js.native

  /** Only for raw */
  @JSName("on")
  def raw(event: String, cb: js.Function3[js.Dynamic, String, js.Dynamic, Unit]): FSWatcher = js.native
  def unwatch(path: String | Array[String]): Unit                                           = js.native
  def getWatched(): js.Dictionary[Array[String]]                                            = js.native
  def close(): Unit                                                                         = js.native
}

object FSWatcher {
  val ready     = "ready"
  val addDir    = "addDir"
  val unlinkDir = "unlinkDir"
  val error     = "error"
  val raw       = "raw"
  val add       = "add"
  val change    = "change"
  val unlink    = "unlink"
}

object FSWatcherOps {
  import fs2._
  import cats.effect._

  /** Return chokidar watcher and stream of events. (event, path) */
  def toStream[F[_]](watcher: io.scalajs.nodejs.fs.FSWatcher, enames: Traversable[String] = Nil)(
      implicit F: Effect[F],
      ec: ExecutionContext): Stream[F, (String, String)] =
    for {
      q <- Stream.eval(fs2.async.unboundedQueue[F, (String, String)])
      // Stream that only exists for setting up the never ending flow...
      _ <- Stream.suspend {
        // setup the callback and queueing
        enames foreach { e =>
          watcher.on(
            e,
            (arg: String) => {
              //F.unsafeRunAsync(q.enqueue1((e, arg)))(_ => ())
              //F.runAsync(q.enqueue1((e, arg)))(_ => IO.unit)
              F.runAsync(q.enqueue1((e, arg)))(_ => IO.unit)
            }
          )
        }
        // end this stream immediately after the infinite stream finishes :-)
        Stream.emit(())
      }
      e <- q.dequeue // stream that dequeues forever
    } yield e

}
