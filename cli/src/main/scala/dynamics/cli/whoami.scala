// Copyright (c) 2017 aappddeevv@gmail.com
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package cli

import dynamics.common._

import scala.scalajs.js
import js._
import annotation._
import JSConverters._
import js.Dynamic.{literal => jsobj}

import scala.concurrent._
import fs2._
import cats._
import cats.data._
//import cats.implicits._
import io.scalajs.npm.chalk._
import MonadlessTask._

import dynamics.http._

class WhoAmIActions(context: DynamicsContext) {

  import context._
  implicit val WhoAmIDecoder = EntityDecoder.JsObjectDecoder[WhoAmI]

  def whoami(): Action =
    Kleisli { config =>
      {
        dynclient
          .executeFunction[WhoAmI]("WhoAmI")
          .map { who =>
            println("WhoAmI Results:")
            println(s"""Business Unit Id: ${who.BusinessUnitId}
Organization Unit Id: ${who.OrganizationId}
UserId: ${who.UserId}""")
          }
          .flatMap { _ =>
            Task.now(())
          }
      }
    }
}
