// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package cli

import scala.scalajs.js
import js.annotation._
import js.JSConverters._
import js.Dynamic.{literal => lit}
import io.scalajs.npm.chalk._
import cats._
import cats.data._
import cats.implicits._
import cats.effect._

import dynamics.common._
import dynamics.common.implicits._
import MonadlessIO._
import dynamics.client._
import dynamics.client.implicits._
import dynamics.http.implicits._
import client.common._

class ApplicationActions(val context: DynamicsContext) extends {
  import context._

  def getList(attrs: Seq[String] = Nil) = {
    val q = QuerySpec(select = attrs)
    dynclient.getList[AppModule](q.url("appmodules"))
  }

  protected def filter(r: Traversable[AppModule], filter: Seq[String]) =
    Utils.filterForMatches(r.map(a => (a, Seq(a.name.get, a.description.get))), filter)

  val list = Action { config =>
    println(
      "AppModules: See https://docs.microsoft.com/en-us/dynamics365/customer-engagement/web-api/appmodule?view=dynamics-ce-odata-9")
    val cols = lit(
      // description
      "14" -> lit(width = 40)
    )
    val topts = new TableOptions(border = Table.getBorderCharacters(config.common.tableFormat), columns = cols)

    lift {
      val list = unlift(getList())
      //list.map(a => js.Dynamic.global.console.log("app", a))
      val filtered = filter(list, config.common.filter)
      val data = Seq(
        Seq(
          "#",
          "appmoduleid",
          "appmoduleidunique",
          "name",
          "uniquename",
          "componentstate",
          "organizationid",
          "solutionid",
          "webresourceid",
          "url",
          "formfactor",
          "isdefault",
          "isfeature",
          "ismanaged",
          "description",
        ).map(Chalk.bold(_))) ++
        filtered.zipWithIndex.map {
          case (i, idx) =>
            Seq(
              (idx + 1).toString,
              i.appmoduleid.orEmpty,
              i.appmoduleidunique.orEmpty,
              i.name.orEmpty,
              i.uniquename.orEmpty,
              i.componentstate_fv.orEmpty,
              i._organizationid_value_fv.orEmpty,
              i.solutionid.orEmpty,
              i.webresourceid.orEmpty,
              i.url.orEmpty,
              i.formfactor_fv.orEmpty,
              i.isdefault_fv.orEmpty,
              i.isfeature_fv.orEmpty,
              i.ismanaged_fv.orEmpty,
              i.description.orEmpty,
            )
        }
      val out = Table.table(data.map(_.toJSArray).toJSArray, topts)
      println(out)
    }
  }

  val role = Action { config =>
    IO(println("NOT IMPLEMENTED"))
  }

  def get(command: String): Action = {
    command match {
      case "list" => list
      case "role" => role
      case _ =>
        Action { _ =>
          IO(println(s"applications command '${command}' not recognized."))
        }
    }
  }

}
