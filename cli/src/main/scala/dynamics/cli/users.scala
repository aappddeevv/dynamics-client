// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package cli

import scala.scalajs.js
import js._
import js.annotation._
import JSConverters._
import io.scalajs.nodejs._
import scala.concurrent._
import io.scalajs.util.PromiseHelper.Implicits._
import fs2._
import cats._
import cats.data._
import cats.implicits._
import io.scalajs.npm.chalk._
import js.Dynamic.{literal => jsobj}
import cats.effect._

import dynamics.common._
import dynamics.common.implicits._
import dynamics.client._
import dynamics.client.implicits._
import dynamics.http._
import dynamics.http.implicits._
import MonadlessIO._

@js.native
trait SystemuserJS extends js.Object {
  val firstname: String = js.native
  val lastname: String = js.native
  val fullname: String = js.native
  val systemuserid: String = js.native
  val internalemailaddress: String = js.native

  //@JSName("systemuserroles_association@odata.nextLink")
  //val systemuserroles_association_nl: js.UndefOr[String] = js.native
}

@js.native
trait RoleJS extends js.Object {
  val roleid: String = js.native
  val name: String = js.native
}

trait UsersDAO { 
  val dynclient: DynamicsClient

  def getList() = {
    val query =
      "/systemusers?$select=firstname,lastname,fullname,internalemailaddress"
    dynclient.getList[SystemuserJS](query)
  }

  def getUserByEmail(email: String): IO[Option[SystemuserJS]] = {
    val q = QuerySpec(
      filter=Some(s"internalemailaddress eq '${email}'"),
      expand=Seq(Expand("systemuserroles_association"))
    )
    dynclient.getList[SystemuserJS](q.url("systemusers"))
      .map{ u =>
        if(u.size == 1) Some(u(0))
        else None
      }
  }

  def getRoleByName(name: String): IO[Option[RoleJS]] = {
    val q = QuerySpec(filter = Some(s"name eq '${name}'"))
    dynclient.getList[RoleJS](q.url("roles"))
      .map{ roles =>
        if(roles.size == 1) Some(roles(0))
        else None
      }
  }

  def getRolesForUser(id: String) = {
    val q = QuerySpec(
      properties=Seq(NavProperty("systemuserroles_association"))
    )
    dynclient.getList[RoleJS](q.url(s"systemusers($id)"))
  }

  /** Get user and roles if user does not exist return None. */
  def getUserAndCurrentRolesByEmail(email: String) = {
    getUserByEmail(email)
      .flatMap { userOpt =>
        userOpt.fold(IO.pure(Option.empty[(SystemuserJS, Seq[RoleJS])]))(
          u => getRolesForUser(u.systemuserid)
          .map { roles =>
            Some((u, roles))
          })
      }
  }

  def addRole(systemuserId: String, roleId: String) =
    dynclient.associate(
      "systemusers",
      systemuserId,
      "systemuserroles_association",
      "roles",
      roleId)
}

class UsersActions(context: DynamicsContext) extends UsersDAO {
  import context._
  implicit val dec = JsObjectDecoder[SystemuserJS]
  val dynclient = context.dynclient

  val list = Action { config =>
    getList().map { list =>
      IO {
        println("Users")
        val cols  = jsobj("100" -> jsobj(width = 40))
        val topts = new TableOptions(border = Table.getBorderCharacters(config.common.tableFormat), columns = cols)
        val data: Seq[Seq[String]] =
          Seq(Seq("#", "systemuserid", "lastname", "firstname", "fullname", "internalemailaddress")) ++
        list.zipWithIndex.map {
          case (i, idx) =>
            Seq((idx + 1).toString,
              i.systemuserid,
              i.lastname,
              i.firstname,
              i.fullname,
              i.internalemailaddress)
        }
        val out = Table.table(data.map(_.toJSArray).toJSArray, topts)
        println(out)
      }}
  }

  val addRoles = Action { config =>
    getUserAndCurrentRolesByEmail(config.user.userid.get)
      .flatMap { _ match {
        case None => IO(s"Unknown user.")
        case Some((user, roles)) =>
          val currentRoleNames = roles.map(_.name)
          // filter out existing
          val rolesToAdd = config.user.roleNames.filter(n => !currentRoleNames.contains(n))
          println(s"Roles to be added: ${rolesToAdd}")
          val roleEntitiesToAdd = unlift(rolesToAdd.map(n => getRoleByName(n)).toList.sequence)
            .collect{ case Some(r) => r }
          roleEntitiesToAdd
            .map(r =>
              addRole(user.systemuserid, r.roleid)
                .map(result => (result, r)))
            .toList.sequence
            .map{ _.collect { case (added, role) if(added) => role }}
            .map{ _.map(_.name).mkString(",")}
      }}
  }

  def get(command: String): Action = {
    command match {
      case "list" => list
      case "add-roles" => addRoles
      case _ =>
        Action { _ =>
          IO(println(s"users command '${command}' not recognized."))
        }
    }
  }


}
