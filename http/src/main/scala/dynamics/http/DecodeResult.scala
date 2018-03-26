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
  def apply[A](fa: IO[Either[DecodeFailure, A]]): DecodeResult[A] = EitherT(fa)
  def success[A](a: IO[A]): DecodeResult[A]                       = DecodeResult(a.map(Either.right(_)))
  def success[A](a: A): DecodeResult[A]                           = success(IO.pure(a))
  def failure[A](e: IO[DecodeFailure]): DecodeResult[A]           = DecodeResult(e.map(Either.left(_)))
  def failure[A](e: DecodeFailure): DecodeResult[A]               = failure(IO.pure(e))
  def fail[A]: DecodeResult[A]                                    = failure(IO.pure(MessageBodyFailure("Intentionally failed.")))
}
