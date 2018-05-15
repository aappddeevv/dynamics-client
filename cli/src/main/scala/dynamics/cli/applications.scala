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
import fs2helpers.evalN

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

  def getByUniqueName(uname: String): IO[Option[AppModule]] = {
    val qs = QuerySpec(filter = Some(s"uniquename eq '$uname'"))
    dynclient.getOne[Option[AppModule]](qs.url("appmodules"))(ExpectOnlyOneToOption)
  }

  def getRoleByName(name: String): IO[Option[RoleJS]] = {
    val qs = QuerySpec(filter = Some(s"name eq '$name'"))
    dynclient.getOne[Option[RoleJS]](qs.url("roles"))(ExpectOnlyOneToOption)
  }

  def parallelWithLimit2[A](limit: Int, as: List[IO[A]]): IO[List[A]] =
    as.grouped(limit).toList.flatTraverse(_.parSequence)

  val role = Action { config =>
    val aname = config.appModule.appName.get
    val anameiov = IO.shift *> getByUniqueName(aname).map(_.toValidNel[String](s"Application name not found."))
    // control evaluation with evalN otherwise could just use List(io,io,io,...).parSequence
    val rnamesiov = IO.shift *>
    evalN(config.appModule.roleName.map(name => getRoleByName(name).map(n => n.toValidNel[String](s"Role $name was not found."))))

    val io = (anameiov, rnamesiov).parMapN{ (appv, rolesv) =>
      (appv, rolesv.sequence).mapN { (app, roles) =>
        config.appModule.change match {
          case Some("add") =>
            evalN(roles.map(role => dynclient.associate("appmodules", app.appmoduleid.get, "appmoduleroles_association", "roles", role.roleid, false)))
              .map(_ => s"Roles added.")
          case Some("remove") =>
            evalN(roles.map(role => dynclient.disassociate("appmodules", app.appmoduleid.get, "appmoduleroles_association", Some(role.roleid))))
              .map(_ => s"Roles removed.")
          case _ => IO.pure(s"Unknown role command. Must be add or remove.")
        }
      }}

    io.flatMap {
      case Validated.Invalid(msglist) => IO(msglist.toList.foreach(println))
      case Validated.Valid(iomsg) => iomsg.map(println)
    }
  }

  def get(command: String): Action = {
    command match {
      case "list" => list
      case "roles" => role
      case _ =>
        Action { _ =>
          IO(println(s"applications command '${command}' not recognized."))
        }
    }
  }

}
