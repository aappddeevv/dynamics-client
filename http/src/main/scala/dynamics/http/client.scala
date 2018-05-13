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
  * A thin layer over a HTTP service that adds implcit convenience for finding
  * response decoders and handling unsuccessful (non 200 range) responses. Based
  * on http4s design. The methods in this class do not deal with
  * exceptions/errors but does, when expecting a successful result, translate a
  * non-200 saus to an `UnexpectedStatus` exception and hence uses
  * `MonadError.raiseError` to signal the error.
  */
final case class Client[F[_]](open: Kleisli[F, HttpRequest[F], DisposableResponse[F]], dispose: F[Unit])(
    implicit F: MonadError[F, Throwable]) {

  /** Fetch response, process regardless of status. Very low-level. */
  def fetch[A](request: HttpRequest[F])(f: HttpResponse[F] => F[A]): F[A] =
    open.run(request).flatMap(_.apply(f))

  /** Fetch response, process response with `d` but only if successful status.
    * Throw [[UnexpectedStatus]] otherwise since you cannot use the decoder,
    * which assumes a successful response, if the response is not valid.
    */
  def expect[A](req: HttpRequest[F])(implicit d: EntityDecoder[F, A]): F[A] = {
    fetch(req) {
      case Status.Successful(resp) =>
        d.decode(resp).fold(throw _, identity)
      case failedResponse =>
        F.raiseError(UnexpectedStatus(failedResponse.status, request = Option(req), response = Option(failedResponse)))
    }
  }

  /**
    * Fetch response, process response with `d` regardless of status. Hence your
    * `d` needs to be very general. Throws any exception found in `d`'s returned
    * value, DecodeResult.
    *
    */
  def fetchAs[A](req: HttpRequest[F])(implicit d: EntityDecoder[F, A]): F[A] = {
    fetch(req) { resp =>
      d.decode(resp).fold(throw _, identity)
    }
  }

  /** Same as `fetch` but request is in an effect. */
  def fetch[A](request: F[HttpRequest[F]])(f: HttpResponse[F] => F[A]): F[A] =
    request.flatMap(fetch(_)(f))

  /** Same as `expect` but request is in an effect. */
  def expect[A](req: F[HttpRequest[F]])(implicit d: EntityDecoder[F, A]): F[A] =
    req.flatMap(expect(_)(d))

  /** Return only the status. */
  def status(req: HttpRequest[F]): F[Status] =
    fetch(req)(resp => F.pure(resp.status))

  /** Conveience. */
  def status(req: F[HttpRequest[F]]): F[Status] = req.flatMap(status)

  /** Creates a funcion that acts like "client.fetch" but without need to call `.fetch`. */
  def toService[A](f: HttpResponse[F] => F[A]): Kleisli[F, HttpRequest[F], A] =
    open.flatMapF(_.apply(f))

  /** Stream the response contents. */
  def streaming[A](req: HttpRequest[F])(f: HttpResponse[F] => Stream[F, A]): Stream[F, A] = {
    Stream
      .eval(open(req))
      .flatMap {
        case DisposableResponse(response, dispose) => f(response).onFinalize(dispose)
      }
  }

  def shutdown() = dispose
}
