// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package http

import scala.scalajs.js
import js.{|, _}
import scala.concurrent.{Future, ExecutionContext}
import js.annotation._
import fs2._
import cats._
import cats.data._
import cats.implicits._
import cats.effect._

import js.JSConverters._
import scala.annotation.implicitNotFound
import scala.collection.mutable

import dynamics.common._
import fs2helpers._

/**
  * General client interface, a thin layer over a service that adds some implcit
  * convenience for finding response decoders and handling unsuccessful (non 200
  * range) responses. Based on http4s design. The methods in this class do not
  * deal with exceptions/errors but does, when expecting a successful result,
  * translate a non-200 saus to an `UnexpectedStatus` exception.
  */
final case class Client(open: Service[HttpRequest, DisposableResponse], dispose: IO[Unit])(
    implicit ehandler: ApplicativeError[IO, Throwable]) {

  /** Fetch response, process regardless of status. Very low-level. */
  def fetch[A](request: HttpRequest)(f: HttpResponse => IO[A]): IO[A] =
    open.run(request).flatMap(_.apply(f))

  /** Fetch response, process response with `d` but only if successful status.
    * Throw [[UnexpectedStatus]] otherwise since you cannot use the decoder,
    * which assumes a successful response, if the response is not valid.
    */
  def expect[A](req: HttpRequest)(implicit d: EntityDecoder[A]): IO[A] = {
    fetch(req) {
      case Status.Successful(resp) =>
        d.decode(resp).fold(throw _, identity)
      case failedResponse =>
        ehandler.raiseError(
          UnexpectedStatus(failedResponse.status, request = Option(req), response = Option(failedResponse)))
    }
  }

  /**
   * Fetch response, process response with `d` regardless of status. Hence your
   * `d` needs to be very general. Throws any exception found in `d`'s returned
   * value, DecodeResult.
   * 
   */
  def fetchAs[A](req: HttpRequest)(implicit d: EntityDecoder[A]): IO[A] = {
    fetch(req) { resp =>
      d.decode(resp).fold(throw _, identity)
    }
  }

  /** Same as `fetch` but request is in an effect. */
  def fetch[A](request: IO[HttpRequest])(f: HttpResponse => IO[A]): IO[A] =
    request.flatMap(fetch(_)(f))

  /** Same as `expect` but request is in an effect. */
  def expect[A](req: IO[HttpRequest])(implicit d: EntityDecoder[A]): IO[A] =
    req.flatMap(expect(_)(d))

  /** Creates a funcion that acts like "client.fetch" but without need to call `.fetch`. */
  def toService[A](f: HttpResponse => IO[A]): Service[HttpRequest, A] =
    open.flatMapF(_.apply(f))

  def streaming[A](req: HttpRequest)(f: HttpResponse => Stream[IO, A]): Stream[IO, A] = {
    Stream
      .eval(open(req))
      .flatMap {
        case DisposableResponse(response, dispose) => f(response).onFinalize(dispose)
      }
  }

  def shutdown() = dispose
}
