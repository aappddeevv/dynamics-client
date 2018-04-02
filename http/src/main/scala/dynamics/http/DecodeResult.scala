// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package http

import scala.scalajs.js
import scala.concurrent.ExecutionContext
import cats._
import cats.data._
import cats.effect._
import cats.implicits._

import dynamics.common._

/**
  * Helper objects to make creating DecodeResults easier.
  */
object DecodeResult {
  def apply[F[_], A](fa: F[Either[DecodeFailure, A]]): DecodeResult[F, A] = EitherT(fa)

  /** Lift an effectful A to a DecodeResult. */
  def success[F[_],A](a: F[A])(implicit F: Functor[F]): DecodeResult[F,A]                       = DecodeResult(a.map(Either.right(_)))

  /** Lift a pure A to a DecodeResult. */
  def success[F[_],A](a: A)(implicit F: Applicative[F]): DecodeResult[F,A]                           = success(F.pure(a))

  /** Lift an effectful DecodeFailure to a DecodeResult. */
  def failure[F[_],A](e: F[DecodeFailure])(implicit F: Functor[F]): DecodeResult[F,A]           = DecodeResult(e.map(Either.left(_)))

  /** Lift an plain DecodeFailure to a DecodeResult. */
  def failure[F[_],A](e: DecodeFailure)(implicit F: Applicative[F]): DecodeResult[F,A]               = failure(F.pure(e))

  /** Return a failure. */
  def fail[F[_],A](implicit F: Applicative[F]): DecodeResult[F,A]                                    = failure(MessageBodyFailure("Intentionally failed."))
}
