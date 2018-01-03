// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics

import scala.scalajs.js
import js._
import fs2._
import cats._
import cats.data._
import cats.implicits._
import cats.effect._

import dynamics.common._
import fs2helpers._
import common.syntax.jsobject._

package object etl {

  /** Common data record format for many ETL functions. */
  type DataRecord = js.Object

  /** Basic transform takes one input and returns a TransformResult wrapped in an effect. */
  type Transform[I, O] = Kleisli[IO, InputContext[I], TransformResult[I, O]]

  /** The result of a transformation. Outputs in Result are wrapped
    * in a Stream effect.
    */
  type TransformResult[I, O] = Either[TransformFailure[I], Result[O]]

  ///** Inner type of the monad transformer TransformResult[I,O] */
  //type TRInner[I,O] = //Either[TransformFailure[I], Result[O]]

  /** Deep copy the input object. The deep copy is _2. */
  val DeepCopy: Pipe[IO, DataRecord, (DataRecord, DataRecord)] =
    _ map (orig => (orig, jsdatahelpers.deepCopy(orig)))

  /** Emit the individual results objects.
    * Since you lose the TransformResult, you will want to log
    * or process the result prior to this. This pipe is typically used
    * between successive pipe that are running different transforms
    * and you don't care about the source tag.
    */
  def EmitResultData[I, O](): Pipe[IO, TransformResult[I, O], O] =
    EmitResult[I, O] andThen (_.flatMap(_.output))

  /** Emits individual result objects but pairs each output O ith the source tag. */
  def EmitResultDataWithTag[I, O](): Pipe[IO, TransformResult[I, O], (O, String)] =
    EmitResult[I, O] andThen {
      _.flatMap { r =>
        r.output.map(d => (d, r.source))
      }
    }

  /** Emit valid result part of TransformResult or emit an empty stream.
    * Left transform result errors are silently dropped.
    */
  def EmitResult[I, O]: Pipe[IO, TransformResult[I, O], Result[O]] =
    _.collect { case Right(result) => result }
  //_.evalMap(_.value).collect { case Right(result) => result }

  /** Use Result output to create InputContext. */
  def ResultToInputContext[I, O]: Pipe[IO, Result[O], InputContext[O]] =
    _ flatMap { r =>
      r.output.map(InputContext[O](_, r.source))
    }

  /** Make a pipe from a transform. The transform is eval'd in this pipe.*/
  def mkPipe[I, O](t: Transform[I, O]): Pipe[IO, InputContext[I], TransformResult[I, O]] =
    _ evalMap { t(_) }

  /**
    * Transform that drops, renames and filters attributes on the input record.
    */
  def FilterAttributes(drops: Seq[String] = Nil,
                       renames: Seq[(String, String)] = Nil,
                       keeps: Seq[String] = Nil): Transform[DataRecord, DataRecord] =
    Transform.instance { input: InputContext[DataRecord] =>
      import dynamics.common.syntax.jsobject._
      IO {
        val j0 = jsdatahelpers.updateObject(drops, renames, input.input.asDict[js.Any])
        val j1 = jsdatahelpers.keepOnly(j0, keeps: _*)
        TransformResult.success(Result(input.source, Stream.emit(j1.asInstanceOf[js.Object])))
      }
    }

  /** Pipe to mutate a DataRecord. f identifies the object to mutate. Can use `identity`
    * if the object is not wrapped. Easier than using `xf` and `filterAttributesTransform`.
    */
  def UpdateObject[A](drops: Seq[String], renames: Seq[(String, String)], f: A => DataRecord): Pipe[IO, A, A] =
    _ map { a =>
      jsdatahelpers.updateObject[A](drops, renames, f(a).asAnyDict)
      a
    }

  def LogErrorsOnly[O](r: Result[O]) = None

  /** A logger pipe. Logs errors mostly. */
  def LogTransformResult[I, O](
      f: Result[O] => Option[String] = LogErrorsOnly[O] _): Pipe[IO, TransformResult[I, O], TransformResult[I, O]] =
    _ map {
      _ bimap (e => {
        println(s"${e.getMessage}")
        println(s"Record ${e.source.getOrElse("<no source info>")}")
        e.messages.foreach(println)
        e.cause.foreach { t =>
          println(s"Cause: ${t.getMessage}")
        }
        e
      },
      r => {
        f(r).foreach { m =>
          println(m)
          r.messages.foreach(println)
        }
        r
      })
    }

  def DropTake[A](drop: Int, take: Int): Pipe[IO, A, A] =
    _.drop(drop).take(take)

  def PrintIt[A](marker: String = ">"): Pipe[IO, A, A] =
    _ evalMap { a =>
      IO { println(s"$marker $a"); a }
    }

}
