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

  /** Obtain the body as a specific type inside the effect.
    * A decode exception is translated into
    * a failed Task. If the DecodeResult was already failed, that failure is kept.
    */
  final def as[T](implicit decoder: EntityDecoder[T]): Task[T] =
    //attemptAs(decoder).fold(throw _, identity) // less efficient than below...
    attemptAs(decoder).fold(Task.fail(_), _.pure[Task]).flatten
}

/** Superclass of requests and responses. Holds headers and a body
  * at a minimum.
  */
trait Message extends MessageOps {
  def headers: HttpHeaders
  //def body: EntityBody
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

/** Allow the response object to be consumed and call an effect
  * after its has been consumed.
  */
case class DisposableResponse(response: HttpResponse, dispose: Task[Unit]) {
  def apply[A](f: HttpResponse => Task[A]): Task[A] = {
    val task = try f(response)
    catch { case e: Throwable => Task.fail(e) }
    task.attempt.flatMap(result => dispose.flatMap(_ => result.fold[Task[A]](Task.fail, Task.now)))
  }
}

/** Superclass of all error messages from Client. */
sealed abstract class MessageFailure extends RuntimeException {
  def message: String
  final override def getMessage: String = message
}

/** Error for a Client to throw when something happens underneath it e.g. in the OS. */
final case class CommunicationsFailure(details: String, val cause: Option[Throwable] = None) extends MessageFailure {
  cause.foreach(initCause)
  def message: String = s"Communications failure: $details"
}

/** Error for a Client when decoding fails. This means the HTTP message went through. */
sealed abstract class DecodeFailure extends MessageFailure {
  def cause: Option[Throwable]     = None
  override def getCause: Throwable = cause.orNull
}

final case class MessageBodyFailure(details: String, override val cause: Option[Throwable] = None)
    extends DecodeFailure {
  def message: String = s"Malformed body: $details"
}

final case class MissingExpectedHeader(details: String, override val cause: Option[Throwable] = None)
    extends DecodeFailure {
  def message: String = s"Expected header: $details"
}

final case class OnlyOneExpected(details: String, override val cause: Option[Throwable] = None) extends DecodeFailure {
  def message: String = s"Expected one: $details"
}

/** Unexpected status returned, the original request and response may be available. */
final case class UnexpectedStatus(status: Status,
                                  request: Option[HttpRequest] = None,
                                  response: Option[HttpResponse] = None)
    extends RuntimeException

/**
  * General client interface. Based on http4s.
  */
final case class Client(open: Service[HttpRequest, DisposableResponse], dispose: Task[Unit]) {

  /** Fetch response, process regardless of status. Very low-level. */
  def fetch[A](request: HttpRequest)(f: HttpResponse => Task[A]): Task[A] =
    open.run(request).flatMap(_.apply(f))

  /** Fetch response, process response with `d` but only if successful status.
    * Throw [[UnexpectedStatus]] otherwise.
    */
  def expect[A](req: HttpRequest)(implicit d: EntityDecoder[A]): Task[A] = {
    fetch(req) {
      case Status.Successful(resp) =>
        d.decode(resp).fold(throw _, identity)
      case failedResponse =>
        Task.fail(UnexpectedStatus(failedResponse.status, request = Option(req), response = Option(failedResponse)))
    }
  }

  /** Fetch response, process response with `d` regardless of status. */
  def fetchAs[A](req: HttpRequest)(implicit d: EntityDecoder[A]): Task[A] = {
    fetch(req) { resp =>
      d.decode(resp).fold(throw _, identity)
    }
  }

  def fetch[A](request: Task[HttpRequest])(f: HttpResponse => Task[A]): Task[A] =
    request.flatMap(fetch(_)(f))

  def expect[A](req: Task[HttpRequest])(implicit d: EntityDecoder[A]): Task[A] =
    req.flatMap(expect(_)(d))

  def toService[A](f: HttpResponse => Task[A]): Service[HttpRequest, A] =
    open.flatMapF(_.apply(f))

  def streaming[A](req: HttpRequest)(f: HttpResponse => Stream[Task, A]): Stream[Task, A] = {
    Stream
      .eval(open(req))
      .flatMap {
        case DisposableResponse(response, dispose) => f(response).onFinalize(dispose)
      }
  }

  def shutdown() = dispose
}
