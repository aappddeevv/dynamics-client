// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package common

import scala.scalajs._
import js._
import annotation._
import io.scalajs.nodejs._
import io.scalajs.nodejs.buffer.Buffer
import io.scalajs.nodejs.fs._
import io.scalajs.util.PromiseHelper._
import scala.concurrent.Future
import io.scalajs.RawOptions
import io.scalajs.nodejs.stream.Writable
import io.scalajs.nodejs.events.IEventEmitter

@js.native
@JSImport("fs-extra", JSImport.Namespace)
object FsExtra extends js.Object {
  def mkdirs(path: String, callback: js.Function1[js.Error, Unit]): Unit = js.native

  def outputFile(file: String,
                 data: String | io.scalajs.nodejs.buffer.Buffer,
                 options: FileOutputOptions = null,
                 callback: FsCallback0): Unit = js.native
}

object Fse {
  def outputFile(file: String,
                 data: String | io.scalajs.nodejs.buffer.Buffer,
                 options: FileOutputOptions = null): Future[Unit] =
    promiseWithError0[FileIOError](FsExtra.outputFile(file, data, options, _))
}

@js.native
@JSImport("process", JSImport.Namespace)
object processhack extends js.Object {
  def hrtime(previous: UndefOr[Array[Int]] = js.undefined): Array[Int] = js.native
}

@js.native
@JSImport("glob", JSImport.Namespace)
object glob extends js.Object {
  @JSName("sync")
  def apply(pattern: String, options: js.Dynamic = js.Dynamic.literal()): Array[String] = js.native
}

/**
  * Uses https://github.com/uhop/stream-json
  */
@js.native
trait Source extends js.Object {
  val input: io.scalajs.nodejs.stream.Writable  = js.native
  val output: io.scalajs.nodejs.stream.Readable = js.native
  val streams: js.Array[js.Any]                 = js.native
}

/**
  * Uses https://github.com/uhop/stream-json
  */
@js.native
@JSImport("stream-json/utils/StreamJsonObjects", JSImport.Namespace)
object StreamJsonObjects extends js.Object {
  def make(options: js.UndefOr[RawOptions] = js.undefined): Source = js.native
}

/**
  * Uses https://github.com/uhop/stream-json
  */
@js.native
//@JSImport("stream-json/utils/StreamArray", JSImport.Namespace)
object StreamArray extends js.Object {
  def make(options: js.UndefOr[RawOptions] = js.undefined): Source = js.native
}

