// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package cli

import scala.scalajs.js
import js.|
import js.annotation._

import io.scalajs.RawOptions
import io.scalajs.nodejs.Error
import io.scalajs.nodejs
import io.scalajs.nodejs.buffer.Buffer
import io.scalajs.nodejs.stream.{Readable, Writable}
import io.scalajs.nodejs.events.IEventEmitter
import io.scalajs.util.PromiseHelper.Implicits._
import io.scalajs.nodejs.fs
import io.scalajs.nodejs.fs._

object CSVStringifyX {

  @js.native
  trait CSVStringify extends IEventEmitter {

    /** Streaming version. */
    def apply(): Stringifier = js.native

    /** Streaming version. */
    def apply(options: StringifyOptions | RawOptions): Stringifier = js.native

    /** Callback version. */
    def apply(data: js.Array[js.Array[String]], callback: js.Function2[nodejs.Error, String, js.Any]): Unit = js.native

    /** Callback version.*/
    def apply(data: js.Array[js.Array[String]],
              options: StringifyOptions | RawOptions,
              callback: js.Function2[nodejs.Error, String, js.Any]): Unit = js.native
  }

  /** Allows you to register for events. */
  @js.native
  class Stringifier() extends Readable with Writable {
    def write(data: js.Array[_]): Boolean = js.native
    def write(data: js.Object): Boolean   = js.native
  }

  implicit class StringifierEvents(val s: Stringifier) extends AnyVal {
    @inline
    def onError(listener: Error => Any): s.type = s.on("error", listener)
    @inline
    def onFinish(listener: () => Any): s.type = s.on("finish", listener)
    @inline
    def onReadable(listener: () => Any): s.type = s.on("readable", listener)
    @inline
    def onRecord(listener: (js.Array[js.Any], Int) => Any): s.type = s.on("record", listener)
  }

  @js.native
  @JSImport("csv-stringify", JSImport.Namespace)
  object CSVStringify extends CSVStringify

  class StringifyOptions(
      val columns: js.UndefOr[js.Dictionary[String] | js.Array[String]] = js.undefined,
      val delimiter: js.UndefOr[String] = js.undefined,
      val escape: js.UndefOr[String] = js.undefined,
      val eof: js.UndefOr[Boolean] = js.undefined,
      val header: js.UndefOr[Boolean] = js.undefined,
      val quote: js.UndefOr[String] = js.undefined,
      val quoted: js.UndefOr[Boolean] = js.undefined,
      val quotedEmpty: js.UndefOr[Boolean] = js.undefined,
      val quotedString: js.UndefOr[Boolean] = js.undefined,
      val rowDelimiter: js.UndefOr[Boolean] = js.undefined,
      val formatters: js.UndefOr[js.Dictionary[js.Function1[js.Any, String]]] = js.undefined
  ) extends js.Object
}
