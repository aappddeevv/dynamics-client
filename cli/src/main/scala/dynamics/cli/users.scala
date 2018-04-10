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
import dynamics.client.common._
import MonadlessIO._

trait UsersDAO {
  val dynclient: DynamicsClient

  def getUsers() = {
    val query =
      "/systemusers?$select=firstname,lastname,fullname,internalemailaddress"
    dynclient.getList[SystemuserJS](query)
  }

  def getRoles(organizationId: Option[String] = None, businessUnitId: Option[String] = None) = {
    val ofilter = organizationId.map(o => s"organizationid eq $o")
    val bfilter = businessUnitId.map(b => s"_businessunitid_value eq $b")
    val q = QuerySpec(
      filter = Option(Seq(ofilter, bfilter).collect { case Some(s) => s }.mkString(" and ")).filterNot(_.isEmpty)
    )
    dynclient.getList[RoleJS](q.url("roles"))
  }

  def getUserByEmail(email: String): IO[Option[SystemuserJS]] = {
    val q = QuerySpec(
      filter = Some(s"internalemailaddress eq '${email}'"),
      expand = Seq(Expand("systemuserroles_association"))
    )
    dynclient
      .getList[SystemuserJS](q.url("systemusers"))
      .map { u =>
        if (u.size == 1) Some(u(0))
        else None
      }
  }

  def getRoleByName(name: String): IO[Option[RoleJS]] = {
    val q = QuerySpec(filter = Some(s"name eq '${name}'"))
    dynclient
      .getList[RoleJS](q.url("roles"))
      .map { roles =>
        if (roles.size == 1) Some(roles(0))
        else None
      }
  }

  def getRolesForUser(id: String) = {
    val q = QuerySpec(
      properties = Seq(NavProperty("systemuserroles_association"))
    )
    dynclient.getList[RoleJS](q.url(s"systemusers($id)"))
  }

  /** Get user and roles if user does not exist return None. */
  def getUserAndCurrentRolesByEmail(email: String) = {
    getUserByEmail(email)
      .flatMap { userOpt =>
        userOpt.fold(IO.pure(Option.empty[(SystemuserJS, Seq[RoleJS])]))(u =>
          getRolesForUser(u.systemuserid).map(roles => Some((u, roles))))
      }
  }

  def addRole(systemuserId: String, roleId: String) =
    dynclient.associate("systemusers", systemuserId, "systemuserroles_association", "roles", roleId, false)

  def removeRole(systemuserId: String, roleId: String) =
    dynclient.disassociate("systemusers", systemuserId, "systemuserroles_association", Some(roleId))

  /**
    * Given a list of string role names, return (valid, invalid, all) role names. "all"
    * is all role (id, name) tuples in the org.
    */
  def validateRoleNames(names: Seq[String]): IO[(Seq[String], Seq[String], Seq[RoleNameJS])] = {
    val q = QuerySpec(select = Seq("roleid", "name"))
    dynclient
      .getList[RoleJS](q.url("roles"))
      .map { list =>
        val inputs   = names.distinct
        val allNames = list.map(_.name)
        val valid    = allNames.intersect(inputs)
        (valid, names diff valid, list)
      }
  }

}

class UsersActions(context: DynamicsContext) extends UsersDAO {
  import context._
  implicit val dec = JsObjectDecoder[SystemuserJS]()
  val dynclient    = context.dynclient

  def renderRoleList(config: CommonConfig, roles: Seq[RoleJS]): String = {
    val cols = jsobj("100" -> jsobj(width = 40))
    val topts = new TableOptions(
      border = Table.getBorderCharacters(config.tableFormat),
      columns = cols
    )
    val data: Seq[Seq[String]] =
      Seq(Seq("#", "roleid", "name", "parentrootrole", "componentstate", "solutionid", "businessunit")) ++
        roles.sortBy(_.name).zipWithIndex.map {
          case (i, idx) =>
            Seq((idx + 1).toString,
                i.roleid,
                i.name,
                i._parentrootroleid_value_fv,
                i.componentstate_fv,
                i.solutionid,
                i._businesunitid_value_fv)
        }
    Table.table(data.map(_.toJSArray).toJSArray, topts)
  }

  val listRoles = Action { config =>
    getRoles()
      .flatMap { list =>
        IO {
          println("Roles")
          println(renderRoleList(config.common, list))
        }
      }
  }

  val listUserRoles = Action { config =>
    getUserAndCurrentRolesByEmail(config.user.userid.get)
      .flatMap {
        _ match {
          case Some((user, roles)) =>
            IO {
              println(s"Roles for ${user.internalemailaddress}")
              println(renderRoleList(config.common, roles))
            }
          case None =>
            IO(println(s"User not found."))
        }
      }
  }

  val list = Action { config =>
    getUsers()
      .flatMap { list =>
        IO {
          //list.foreach(u => println(PrettyJson.render(u)))
          println("Users")
          val cols  = jsobj("100" -> jsobj(width = 40))
          val topts = new TableOptions(border = Table.getBorderCharacters(config.common.tableFormat), columns = cols)
          val data: Seq[Seq[String]] =
            Seq(Seq("#", "systemuserid", "lastname", "firstname", "fullname", "internalemailaddress")) ++
              list.sortBy(_.internalemailaddress).zipWithIndex.map {
                case (i, idx) =>
                  Seq((idx + 1).toString, i.systemuserid, i.lastname, i.firstname, i.fullname, i.internalemailaddress)
              }
          val out = Table.table(data.map(_.toJSArray).toJSArray, topts)
          println(out)
        }
      }
  }

  private def add(user: SystemuserJS,
                  currentRoles: Seq[RoleJS],
                  valid: Seq[String],
                  invalid: Seq[String],
                  allRoles: Seq[RoleNameJS]): IO[String] = {
    val roleNamesToAdd = valid intersect currentRoles.map(_.name)
    val rolesToAdd     = allRoles.filter(r => roleNamesToAdd.contains(r.name))
    //rolesToAdd.foreach{ e => js.Dynamic.global.console.log(e)}
    rolesToAdd
      .map(role => addRole(user.systemuserid, role.roleid).map(wasAdded => (role.name, wasAdded)))
      .toList
      .sequence
      .flatMap { listOfResults =>
        val nonAdds = listOfResults.filterNot(p => p._2).map(_._1)
        val adds    = listOfResults.filter(p => p._2).map(_._1)
        val rolesAddedMsg =
          if (adds.length == 0) s"""No roles added."""
          else s"""Roles added (${listOfResults.length}): ${adds.mkString(", ")}"""
        val rolesNotAdded = s"""Roles not added: ${nonAdds.mkString(", ")}"""
        if (nonAdds.length > 0) IO(s"${rolesAddedMsg}\n${rolesNotAdded}")
        else IO(rolesAddedMsg)
      }
  }

  private def remove(user: SystemuserJS,
                     currentRoles: Seq[RoleJS],
                     valid: Seq[String],
                     invalid: Seq[String],
                     allRoles: Seq[RoleNameJS]): IO[String] = {
    val currentRoleNames  = currentRoles.map(_.name)
    val roleNamesToRemove = valid intersect currentRoleNames
    val rolesToRemove     = allRoles.filter(r => roleNamesToRemove.contains(r.name))
    rolesToRemove
      .map(role => removeRole(user.systemuserid, role.roleid).map(didHappen => (role.name, didHappen)))
      .toList
      .sequence
      .flatMap { listOfResults =>
        val nonRemoves = listOfResults.filterNot(p => p._2).map(_._1)
        val removes    = listOfResults.filter(p => p._2).map(_._1)
        val rolesRemovedMsg =
          if (removes.length == 0) s"""No roles removed."""
          else s"""Roles removed (${listOfResults.length}): ${removes.mkString(", ")}"""
        val rolesNotRemoved = s"""Roles not added: ${nonRemoves.mkString(", ")}"""
        if (nonRemoves.length > 0) IO(s"${rolesRemovedMsg}\n${rolesNotRemoved}")
        else IO(rolesRemovedMsg)
      }
  }

  private def doit(action: (SystemuserJS, Seq[RoleJS], Seq[String], Seq[String], Seq[RoleNameJS]) => IO[String]) =
    Action { config =>
      getUserAndCurrentRolesByEmail(config.user.userid.get)
        .flatMap(userStuff =>
          validateRoleNames(config.user.roleNames)
            .map(roleCheck => (userStuff, roleCheck)))
        .flatMap {
          _ match {
            case (_, (_, invalid, _)) if (invalid.length > 0) =>
              IO.pure(s"""Invalid names: ${invalid.mkString(", ")}""")
            case (None, _) =>
              IO.pure(s"""Invalid user: ${config.user.userid.get}""")
            case (Some((user, currentRoles)), (valid, Nil, allRoles)) =>
              action(user, currentRoles, valid, Nil, allRoles)
            case _ =>
              IO.pure(s"""Internal error. Report this as a bug.""")
          }
        }
        .map(println)
    }

  val addRoles    = doit(add _)
  val removeRoles = doit(remove _)

  def get(command: String): Action = {
    command match {
      case "listUsers"     => list
      case "listUserRoles" => listUserRoles
      case "listRoles"     => listRoles
      case "addRoles"      => addRoles
      case "removeRoles"   => removeRoles
      case _ =>
        Action { _ =>
          IO(println(s"users command '${command}' not recognized."))
        }
    }
  }

}
