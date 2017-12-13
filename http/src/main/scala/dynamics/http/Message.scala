// Copyright (c) 2017 aappddeevv@gmail.com
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package http

import scala.scalajs.js
import js.{|, _}
import scala.concurrent.{Future, ExecutionContext}
import js.annotation._
import fs2._
import fs2.util._
import cats._
import cats.data._
import cats.implicits._
import fs2.interop.cats._
import fs2.Task._

import js.JSConverters._
import scala.annotation.implicitNotFound
import scala.collection.mutable

import dynamics.common._
import fs2helpers._

/**
  * Basic HTTP client code based mostly on http4s.
  */
trait MessageOps {

  /** Attempt to decode the body. */
  def attemptAs[T](implicit decoder: EntityDecoder[T]): DecodeResult[T]

  /**
    * Obtain the body as a specific type inside the effect.
    * A decode exception is translated into
    * a failed Task. If the DecodeResult was already failed, that failure is kept.
    */
  final def as[T](implicit decoder: EntityDecoder[T]): Task[T] =
    //attemptAs(decoder).fold(throw _, identity) // less efficient than below...
    attemptAs(decoder).fold(Task.fail(_), _.pure[Task]).flatten
}

/**
  * Superclass of requests and responses. Holds headers and a body
  * at a minimum.
  */
trait Message extends MessageOps {
  def headers: HttpHeaders
  def body: Entity

  override def attemptAs[T](implicit decoder: EntityDecoder[T]): DecodeResult[T] =
    decoder.decode(this)
}

object HttpHeaders {
  val empty: HttpHeaders = collection.immutable.Map[String, Seq[String]]()

  /** Create headers from pairs of strings. Use something else to add String -> Seq[String]. */
  def apply(properties: (String, String)*): HttpHeaders = {
    val p = properties.map(d => (d._1, Seq(d._2)))
    collection.immutable.Map(p: _*)
  }

  /** Create from String -> Seq[String] pairs. */
  def from(properties: (String, Seq[String])*): HttpHeaders = properties.toMap

  /** Make a Content-ID header. */
  def contentId(id: String) = HttpHeaders("Content-ID" -> id)

  /** Render to a String. Newline is added on end if any content is rendered. */
  def render(h: HttpHeaders): String = {
    val sb = new StringBuilder()
    h.foreach {
      case (k, arr) =>
        val v = h(k).mkString(";")
        sb.append(s"$k: $v\r\n")
    }
    sb.toString()
  }
}

/** Wrapper type for a Method. */
sealed case class Method(name: String)

object Method {
  val GET    = Method("GET")
  val POST   = Method("POST")
  val DELETE = Method("DELETE")
  val PATCH  = Method("PATCH")
  val PUT    = Method("PUT")
}

/** A low-level request. */
case class HttpRequest(method: Method,
                       path: String,
                       headers: HttpHeaders = HttpHeaders.empty,
                       body: Entity = Entity.empty)
    extends Message

/** A low-level response. */
case class HttpResponse(status: Status, headers: HttpHeaders, body: Entity) extends Message

/**
  * A response that allows the response object to used then calls an effect
  * after its has been consumed.
  */
case class DisposableResponse(response: HttpResponse, dispose: Task[Unit]) {
  def apply[A](f: HttpResponse => Task[A]): Task[A] = {
    val task =
      try f(response)
      catch { case e: Throwable => Task.fail(e) }
    task.attempt.flatMap(result => dispose.flatMap(_ => result.fold[Task[A]](Task.fail, Task.now)))
  }
}
