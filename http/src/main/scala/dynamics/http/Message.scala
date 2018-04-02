// Copyright (c) 2017 The Trapelo Group LLC
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
trait MessageOps[F[_]] extends Any {

  /** Attempt to decode the body. */
  def attemptAs[T](implicit F: FlatMap[F], decoder: EntityDecoder[F, T]): DecodeResult[F, T]

  /**
    * Obtain the body as a specific type inside the effect.  A decode exception
    * is translated into a failed effect. If the DecodeResult was already failed,
    * that failure is kept.
    */
  final def as[T](implicit F: FlatMap[F], decoder: EntityDecoder[F, T]): F[T] =
    //attemptAs(decoder).fold(F.raiseError(_), _.pure[F]).flatten
    attemptAs.fold(throw _, identity)
}

/**
  * Superclass of requests and responses. Holds headers and a body
  * at a minimum.
  */
trait Message[F[_]] extends MessageOps[F] {
  def headers: HttpHeaders
  def body: Entity

  override def attemptAs[T](implicit F: FlatMap[F], decoder: EntityDecoder[F, T]): DecodeResult[F, T] =
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
sealed case class Method private (name: String)

object Method {
  val GET    = Method("GET")
  val POST   = Method("POST")
  val DELETE = Method("DELETE")
  val PATCH  = Method("PATCH")
  val PUT    = Method("PUT")

  val all = Seq(GET, POST, DELETE, POST, PUT)
}

trait MethodInstances {

  implicit val showForMethod: Show[Method] = Show.fromToString
}


/** A low-level request. */
case class HttpRequest[F[_]](method: Method,
                       path: String,
                       headers: HttpHeaders = HttpHeaders.empty,
                       body: Entity = Entity.empty)
    extends Message[F]

/** A low-level response. */
case class HttpResponse[F[_]](status: Status, headers: HttpHeaders, body: Entity) extends Message[F]

/**
  * A response that allows the response object to used then calls an effect
  * after its has been consumed via `apply`. This is essentially a resource
  * management hook.
  */
final case class DisposableResponse[F[_]](response: HttpResponse[F], dispose: F[Unit]) {
  def apply[A](f: HttpResponse[F] => F[A])(implicit F: MonadError[F, Throwable]): F[A] = {
    val task =
      try f(response)
      catch {
        case e: Throwable =>
          // log something here..
          F.raiseError(e)
      }

    for {
      result <- task.attempt
      _ <- dispose
      fold <- result.fold[F[A]](F.raiseError, F.pure)
    } yield fold
  }
}
