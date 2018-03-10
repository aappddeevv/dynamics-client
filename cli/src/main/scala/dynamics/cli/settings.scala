// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package cli

import scala.scalajs.js
import js._
import js.annotation._
import io.scalajs.nodejs._
import fs2._
import js.JSConverters._
import cats._
import cats.data._
import cats.implicits._
import js.Dynamic.{literal => jsobj}
import cats.effect._

import common._
import MonadlessIO._
import common.Utils._
import common.implicits._
import dynamics.http._
import dynamics.http.implicits._
import client._
import client.implicits._

/**
  * Perform a post on an undocumented API to set an org's settings.  You will
  * need to prepare a config file in the specific format to post as the body.
  *
  */
class SettingsActions(context: DynamicsContext) extends LazyLogger {

  import SettingsActions._
  import context._

  val POSTFIX             = "/Tools/SystemSettings/cmds/cmd_update.aspx"
  val defaultSettingsFile = "org-settings.xml"

  /**
    * We need a settings.xml file and the proper https URL which is in
    * connectInfo.acquireTokenResource.
    */
  def post(): Action = Kleisli { config =>
    (context.appConfig.common.connectInfo.acquireTokenResource.toOption,
     config.settings.settingsFile orElse Some(defaultSettingsFile)) match {
      case (Some(url), Some(settingsFile)) if (Utils.fexists(settingsFile)) =>
        val body = Utils.slurp(settingsFile)
        val headers = HttpHeaders(
          "accept"       -> "*/*",
          "Content-Type" -> "text/plain"
        )
        val request = HttpRequest(Method.POST, url + "/" + POSTFIX, headers, Entity.fromString(body))
        val resp = dynclient.http.fetch(request) {
          case Status.Successful(r) =>
            if (config.debug) println(s"settings post response $r")
            IO.pure("Settings applied.")
          case bad =>
            if (config.debug) println(s"settings post response $bad")
            bad.body.map(body => IO.pure(s"Error posting setting ${bad.status.code}\n${body}"))
        }
        resp.map(println)
      case _ =>
        IO(println(s"Settings files must be readable and acquireTokenResource must be set in the connecton info."))
    }
  }

  def get(command: String): Action = {
    command match {
      case "post" => post()
      case _ =>
        Action { _ =>
          IO(println(s"settings command '${command}' not recognized."))
        }
    }
  }
}

object SettingsActions {}
