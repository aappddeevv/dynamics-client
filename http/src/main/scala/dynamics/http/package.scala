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

  type Service[A, B]      = Kleisli[IO, A, B]
  type Middleware         = Client => Client
  type HttpService        = Service[HttpRequest, HttpResponse]
  type StreamingClient[A] = fs2.Pipe[IO, HttpRequest, IO[A]]

  /** Non-streaming but good enough for our needs. */
  type Entity = IO[String]

  /** Basic headers are a dict of strings. */
  type HttpHeaders = collection.immutable.Map[String, Seq[String]]

  /**
    * When decoding a response body, either you get an A
    * or a DecodeFailure. The Task may also carry an exception.
    */
  type DecodeResult[A] = EitherT[IO, DecodeFailure, A]

  /** 
   * Retry policies are added to an effect so that when run, a retry
   * occurs as needed.
   */
  type RetryPolicy[F[_], A] = F[A] => F[A]
}
