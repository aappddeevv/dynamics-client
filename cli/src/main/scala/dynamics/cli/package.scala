// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics

import scala.concurrent.{Future, ExecutionContext}
import scala.scalajs.js
import js._

import cats._
import cats.data._
import cats.implicits._
import fs2._
import js.JSConverters._
import cats.effect._

package object cli {

  /** The dynamics program runs "actions" */
  type Action = Kleisli[IO, AppConfig, Unit]

  object Action {
    def apply(f: AppConfig => IO[Unit]): Action = Kleisli(f)
  }

  /** An action that does need the arguments. */
  def NoArgAction(block: => Unit) = Action {  _ =>
      IO { block }
    }

  /** Select an action to run or return None. */
  type ActionSelector = (AppConfig, DynamicsContext) => Option[Action]

  def doNothing1[A] = (_: A) => IO.pure(())

  /** Must not be null or js.undefined. */
  def isDefined(a: js.Any): Boolean = a != null && !js.isUndefined(a)


  /** Default connection config resource. */
  val defaultConfigFile = "dynamics.json"
}
