// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package client

import cats.effect._

import dynamics.common._
import dynamics.common.implicits._
import dynamics.cli._
import dynamics.client._
import dynamics.http.implicits._

class ActionActions(ctx: DynamicsContext) extends LazyLogger {
  import ctx._
  import dynclient._

  /**
    * Execute an action using the dynamics client.
    */
  val execute = Action { config =>
    val body = config.action.payloadFile.fold("")(f => Utils.slurp(f))
    executeAction[String](config.action.action, body.toEntity._1)
      .map { r =>
        if (config.action.pprint) PrettyJson.render(Utils.jsonParse(r).asJsObj)
        else r
      }
      .flatMap { result =>
        IO(println(s"Payload response:\n$result"))
      }
  }

  def get(command: String): Action =
    command match {
      case "execute" => execute
      case _ =>
        Action { _ =>
          IO(println(s"action command '${command}' not recognized."))
        }
    }
}
