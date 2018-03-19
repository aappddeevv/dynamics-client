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

object Utils {

  /** Add minutes to a date. */
  def addMinutes(d: js.Date, n: Int): js.Date =
    new js.Date(d.getTime() + (n * 60 * 1000))

  /** Empty dynamics object read to add options in a javascript way. */
  def options() = js.Dynamic.literal()

  /** Pretty print a js.Object */
  def pprint(o: js.Object, opts: nodejs.util.InspectOptions = new nodejs.util.InspectOptions(depth = null, maxArrayLength = 10)) =
    nodejs.util.Util.inspect(o, opts)

  /** Render a js.Any into a string using nodejs Inspect. */
  def render(o: js.Any, opts: nodejs.util.InspectOptions = new nodejs.util.InspectOptions(depth = null, maxArrayLength = 10)) =
    nodejs.util.Util.inspect(o, opts)

  /** Pretty print a js.Dynamic object. */
  def pprint(o: js.Dynamic): String = pprint(o.asInstanceOf[js.Object])

  /** Slurp a file as JSON and cast. No exception handling is provided. Synchronous. */
  def slurpAsJson[T](file: String, encoding: String = "utf8"): T = {
    val str = io.scalajs.nodejs.fs.Fs.readFileSync(file, encoding)
    js.JSON.parse(str).asInstanceOf[T]
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

  /** Infer a resource type based on the filename extension. Return none if
    * it does not match any known Web Resource resource types.
    */
  def inferWebResourceType(ext: String): Option[Int] =
    WebResource.allowedExtensions.get(ext.toLowerCase)

  /** Factory to create a matcher.
    * @returns A => Boolean
    */
  def filterOneForMatches[A](f: A => Seq[String], filters: Traversable[String] = Nil) = {
    val counter = matchCount(filters)
    (item: A) =>
      {
        val keys   = f(item)
        val counts = keys.map(counter(_)).sum
        counts > 0
      }
  }

  /** Given a set of regex filters, return String => Int that counts matches. */
  def matchCount(filters: Traversable[String]) = {
    import scala.util.matching.Regex
    val regexList =
      if (filters.size == 0) Seq(new Regex(".*"))
      else filters.map(new Regex(_))

    (item: String) =>
      {
        regexList
          .map(_.findAllMatchIn(Option(item).getOrElse("")).size)
          .collect { case l: Int if (l > 0) => true }
          .size
      }
  }

  /**
    * Given a sequence of data items and related "use these strings for
    * comparison" values, return the data items that matched. If filters is
    * empty, every item matches.
    */
  def filterForMatches[A](wr: Traversable[(A, Seq[String])], filters: Traversable[String] = Nil): Seq[A] = {
    val counter = matchCount(filters)
    wr.map(d => {
        val keys   = d._2
        val counts = keys.map(counter(_)).sum
        (d._1, counts > 0)
      })
      .filter(_._2)
      .toSeq
      .map(_._1)
  }

  /** Return the tail of the path from the prefix forward.
    * It does not matter if the prefix is a directory segment
    * or part of a filename.
    */
  def stripUpTo(path: String, prefix: String): String = {
    val idx = path.indexOf(prefix)
    if (idx >= 0) path.substring(idx)
    else path
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

  /** Returns true if the file exists, false otherwise. */
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

  /** Merge two js.Objects in scale. */
  def mergeJSObjects[A <: js.Object](objs: A*): A = {
    val result = js.Dictionary.empty[Any]
    for (source <- objs) {
      for ((key, value) <- source.asInstanceOf[js.Dictionary[Any]])
        result(key) = value
    }
    result.asInstanceOf[A]
  }

  /** Generate a new CRM GUID. */
  def CRMGUID(): String = java.util.UUID.randomUUID.toString

  /** Parse some json. */
  def jsonParse(content: String): js.Dynamic = js.JSON.parse(content)

}
