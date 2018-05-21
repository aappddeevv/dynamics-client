// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package common

import java.io._
import scala.scalajs.js
import js.|
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

  /**
    * This is really just a Semigroup "combine" operation but it does *not* use
    * "combine" at lower levels of the structure i.e. a shallow copy. Last
    * object's fields wins. Handles null inputs.
    *
    * @see https://stackoverflow.com/questions/36561209/is-it-possible-to-combine-two-js-dynamic-objects
    */
  @inline def mergeJSObjects(objs: js.Dynamic*): js.Dynamic = {
    // not js.Any? maybe keep js or scala values in here....
    val result = js.Dictionary.empty[Any] // js.Any?
    for (source <- objs) {
      for ((key, value) <- if (source != null) source.asInstanceOf[js.Dictionary[Any]] else js.Dictionary.empty[Any]) // js.Any?
        result(key) = value
    }
    result.asInstanceOf[js.Dynamic]
  }

  /**
    * Merge objects and Ts together. Good for merging props with data-
    * attributes. Handles null inputs.  See the note from
    * [[mergeJSObjects]]. Last object's fields win.
    */
  @inline def merge[T <: js.Object](objs: T | js.Dynamic*): T = {
    val result = js.Dictionary.empty[Any]
    for (source <- objs) {
      for ((key, value) <- if (source != null) source.asInstanceOf[js.Dictionary[Any]] else js.Dictionary.empty[Any])
        result(key) = value
    }
    result.asInstanceOf[T]
  }

  /** Generate a new CRM GUID. */
  @inline def CRMGUID(): String = java.util.UUID.randomUUID.toString

  /** Parse some json. */
  @inline def jsonParse(content: String, reviver: Option[Reviver] = None): js.Dynamic =
    js.JSON.parse(content, reviver.getOrElse(js.undefined.asInstanceOf[Reviver]))

  /** Clean an id that has braces around it. */
  @inline def cleanId(id: String): String = id.stripSuffix("}").stripPrefix("{").trim

  /** Given a throwable, convert the stacktrace to a string for printing. */
  def getStackTraceAsString(t: Throwable): String = {
    val sw = new StringWriter
    t.printStackTrace(new PrintWriter(sw))
    sw.toString
  }

  /** Strip a string suitable for a text attribute in dynamics. Essentially, it
   * preserves some ASCII characters and leaves a few whitespace control
   * characters.
   */
  def strip(in: String): String =
    in.replaceAll("[\\p{Cntrl}&&[^\n\t\r]]", "").replaceAll("\\P{InBasic_Latin}", "")

}
