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
