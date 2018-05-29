// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics

import scala.collection.mutable

import cats._
import cats.data._
import fs2._
import cats.effect._

package object http {

  @deprecated("Use Kleisli directly e.g. Kleisli[F, A, B]", "0.1.0")
  type Service[F, A, B]         = Kleisli[IO, A, B]
  type Middleware[F[_]]         = Client[F] => Client[F]
  type HttpService[F[_]]        = Kleisli[F, HttpRequest[F], HttpResponse[F]]
  type StreamingClient[F[_], A] = fs2.Pipe[F, HttpRequest[F], F[A]]

  /**
    * Non-streaming but good enough for our needs. IO is used explicitly but
    * since we have strict bodies for dynamics, why not use "Id" in the short
    * term? Entity is the body part of a Message.
    *
    * @todo Make F so we can use Id or something simpler.
    */
  type Entity = IO[String]

  /** Basic headers are a dict of strings. */
  type HttpHeaders = collection.immutable.Map[String, Seq[String]]

  /**
    * When decoding a response body, either you get an A or a DecodeFailure. The
    * effect may also carry an exception.  EitherT has a bunch of combinators.
    *
    * @see https://typelevel.org/cats/api/cats/data/EitherT.html
    */
  type DecodeResult[F[_], A] = EitherT[F, DecodeFailure, A]

  /**
    * Retry policies are added to an effect so that when run, a retry
    * occurs as needed.
    */
  type RetryPolicy[F[_], A] = F[A] => F[A]
}
