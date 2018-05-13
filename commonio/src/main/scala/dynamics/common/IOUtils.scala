// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package common

import scala.scalajs.js
import js.|
import io.scalajs.nodejs
import io.scalajs.nodejs._
import io.scalajs.nodejs.path.Path
import io.scalajs.nodejs.buffer.Buffer
import fs._
import path._
import scala.concurrent._
import cats.effect._
import cats._
import cats.data._
import cats.implicits._

import dynamics.common.Utils._

object IOUtils {

  /** Pretty print a js.Object */
  def pprint(o: js.Object,
             opts: nodejs.util.InspectOptions = new nodejs.util.InspectOptions(depth = null, maxArrayLength = 10)) =
    nodejs.util.Util.inspect(o, opts)

  /** Render a js.Any into a string using nodejs Inspect. */
  def render(o: js.Any,
             opts: nodejs.util.InspectOptions = new nodejs.util.InspectOptions(depth = null, maxArrayLength = 10)) =
    nodejs.util.Util.inspect(o, opts)

  /** Pretty print a js.Dynamic object. */
  def pprint(o: js.Dynamic): String = pprint(o.asInstanceOf[js.Object])

  /** Slurp a file as JSON and cast. No exception handling is provided. Synchronous. */
  def slurpAsJson[T](file: String, reviver: Option[Reviver] = None,
    encoding: String = "utf8"): T = {
    val str = io.scalajs.nodejs.fs.Fs.readFileSync(file, encoding)
    js.JSON.parse(str,
      reviver.getOrElse(js.undefined.asInstanceOf[Reviver])
    ).asInstanceOf[T]
  }

  /** Slurp a file as a utf8 string, synchronous. */
  def slurp(file: String, encoding: String = "utf8"): String = {
    io.scalajs.nodejs.fs.Fs.readFileSync(file, encoding)
  }

  /** Get the extension/postfix on a filename less
    * the preceeding ".".
    */
  def getPostfix(path: String): Option[String] = {
    Option(Path.extname(path)).filter(_.length > 0).map(_.substring(1))
  }

  /** Async write content to file creating paths if path
    * contains path segments that do not exist.
    * @param path Path name, both path and file.
    * @param content String content.
    * @return Unit if file written, otherwise a failed IO.
    */
  def writeToFile(path: String, content: String | Buffer)(implicit ec: ExecutionContext): IO[Unit] = {
    IO.fromFuture(IO(Fse.outputFile(path, content)))
  }

  /** Write to file synchronously. */
  def writeToFileSync(path: String, content: String | Buffer): Unit = {
    Fs.writeFileSync(path, content)
  }

  /** Convert string from base64. */
  def fromBase64(s: String) = Buffer.from(s, "base64")

  /** Join two path segments. */
  def pathjoin(lhs: String, rhs: String) =
    Path.join(lhs, rhs)

  /** Returns true if the file exists, false otherwise. Synchronous. */
  def fexists(path: String) =
    try { Fs.accessSync(path, Fs.R_OK); true } catch { case scala.util.control.NonFatal(_) => false }

  /** Get an extension on a file or None. */
  def extension(path: String): Option[String] =
    Option(Path.extname(path)).map(_.substring(1)).filterNot(_.isEmpty)

  /** Get the base(name) + extension. */
  def namepart(path: String): Option[String] =
    Option(Path.parse(path)).map(_.name).filterNot(_.isEmpty).map(_.get)

  /** Get the filename (base + ext) if it exists. */
  def filename(path: String): Option[String] =
    Option(Path.basename(path)).filterNot(_.isEmpty)
}
