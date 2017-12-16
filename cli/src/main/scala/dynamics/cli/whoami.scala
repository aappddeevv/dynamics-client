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
import io.scalajs.npm.chalk._
import MonadlessIO._
import cats.effect._

import dynamics.http._
import dynamics.http.instances.entityDecoder._

class WhoAmIActions(context: DynamicsContext) {

  import context._
  implicit val WhoAmIDecoder = JsObjectDecoder[WhoAmI]

  def whoami() = Action { config =>
    dynclient
      .executeFunction[WhoAmI]("WhoAmI")
      .map { who =>
        println("WhoAmI Results:")
        println(s"""Business Unit Id: ${who.BusinessUnitId}
Organization Unit Id: ${who.OrganizationId}
UserId: ${who.UserId}""")
      }
      .flatMap { _ =>
        IO.pure(())
      }
  }
}
