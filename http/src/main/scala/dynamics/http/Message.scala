// Copyright (c) 2017 aappddeevv@gmail.com
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package http

import scala.scalajs.js
import scala.concurrent.ExecutionContext
import js.annotation._
import cats._
import cats.data._
import cats.implicits._
import cats.effect._
import js.JSConverters._
import scala.annotation.implicitNotFound
import scala.collection.mutable

import dynamics.common._

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
  final def as[T](implicit decoder: EntityDecoder[T], ehandler: ApplicativeError[IO, Throwable]): IO[T] =
    //attemptAs(decoder).fold(throw _, identity) // less efficient than below...
    attemptAs(decoder).fold(ehandler.raiseError(_), _.pure[IO]).flatten
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
case class DisposableResponse(response: HttpResponse, dispose: IO[Unit]) {
  def apply[A](f: HttpResponse => IO[A])(implicit ehandler: ApplicativeError[IO,Throwable]): IO[A] = {
    val task =
      try f(response)
      catch { case e: Throwable => ehandler.raiseError(e) }
    task.attempt.flatMap(result => dispose.flatMap(_ => result.fold[IO[A]](IO.raiseError, IO.pure)))
  }
}
