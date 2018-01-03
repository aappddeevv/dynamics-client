// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package etl

import scala.scalajs.js
import fs2._
import cats._
import cats.data._
import cats.syntax.either._
import cats.effect._

import dynamics.common._

/**
  * TODO: Make extensible.
  */
case class InputContext[A](
    /** Transform input. */
    input: A,
    /**
      * User oriented source marker for processing reporting.
      * Should be short. May be printed multiple times. Often
      * a primary or natural key from `input` but could also
      * be a simple record number.
      */
    source: String
)

/** A result is really a fancy tuple of the input and output along
  * with some result information.
  */
case class Result[O](
                     /** Input identifier. */
                     source: String,
                     /** Output. One input could result in multiple outputs. Stream is an effect. */
                     output: Stream[IO, O],
                     /** Messages, if any */
                     messages: Seq[String] = Nil)

abstract sealed trait TransformFailure[I] extends RuntimeException {
  def message: String
  final override def getMessage: String = message
  def cause: Option[Throwable]          = None
  override def getCause: Throwable      = cause.orNull
  def input: Option[InputContext[I]]
  def source: Option[String]
  def messages: Seq[String]
}

final case class ProcessingError[I](details: String,
                                    source: Option[String] = None,
                                    input: Option[InputContext[I]] = None,
                                    messages: Seq[String] = Nil,
                                    override val cause: Option[Throwable] = None)
    extends TransformFailure[I] {
  def message: String = s"Transform error: $details"
}

object TransformResult {
  // def apply[I,O](fa: Task[Either[TransformFailure[I], Result[O]]]): TransformResult[I,O] = EitherT(fa)
  // def success[I,O](o: Task[Result[O]]): TransformResult[I,O] = TransformResult(o.map(Either.right(_)))
  // def success[I,O](o: Result[O]): TransformResult[I,O] = success(Task.now(o))
  // def failure[I,O](e: Task[TransformFailure[I]]): TransformResult[I,O] = TransformResult(e.map(Either.left(_)))
  // def failure[I,O](e: TransformFailure[I]): TransformResult[I,O] = failure(Task.now(e))

  def success[I, O](o: Result[O]): TransformResult[I, O]           = Either.right(o)
  def failure[I, O](e: TransformFailure[I]): TransformResult[I, O] = Either.left(e)
}

/** Tracks names to objects that can transform data
  * prior to some operation.
  */
object Transform {

  /** Create an instance of Transform from f. */
  //def instance[I,O](f: InputContext[I] => Task[TransformResult[I,O]]): Transform[I,O] = Kleisli { f(_) }
  def instance[I, O](f: InputContext[I] => IO[TransformResult[I, O]]): Transform[I, O] = Kleisli(f)

  /** Identity transform. */
  def identity[A]: Transform[A, A] = instance { input: InputContext[A] =>
    IO(TransformResult.success(Result(input.source, Stream.emit(input.input))))
  }
}

/**
  * Contains a transform and other metadata needed to automatically pre- and post-process
  * an input record.
  */
trait Operator {}
