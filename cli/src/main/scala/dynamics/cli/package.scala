// Copyright (c) 2017 aappddeevv@gmail.com
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

package object cli {

  /** The dynamics program runs "actions" */
  type Action = Kleisli[Task, AppConfig, Unit]

  object Action {
    def apply(f: AppConfig => Task[Unit]): Action = Kleisli(f)
  }

  /** An action that does need the arguments. */
  def NoArgAction(block: => Unit): Action =
    Kleisli { _ =>
      Task.delay { block }
    }

  type ActionSelector = (AppConfig, DynamicsContext) => Option[Action]

  def doNothing1[A] = (_: A) => Task.now(())

  /** Must not be null or js.undefined. */
  def isDefined(a: js.Any): Boolean = a != null && !js.isUndefined(a)

}
