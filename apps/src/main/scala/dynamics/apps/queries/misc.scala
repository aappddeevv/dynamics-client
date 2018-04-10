// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package apps
package queries

import scala.scalajs.js
import concurrent.ExecutionContext
import js.|
import fs2._
import cats._
import cats.data._
import cats.implicits._
import cats.effect._
import js.JSConverters._

import dynamics.client._
import dynamics.cli._
import dynamics.http._
import dynamics.http.implicits._
import dynamics.common._
import dynamics.common.implicits._
import dynamics.client.common._
import dynamics.client.implicits._
import dynamics.client.crmqueryfunctions._
import http.instances.entitydecoder.ValueArrayDecoder

case class RelatedEntity(
  ename: String, esname: String, objectTypeCode: Int,
  roleName: String, roleId: apps.Id,
  id: apps.Id)

/**
 * Used when we need to print out information about a record. Think of this as
 * a generic annotation.
 */
case class FriendlyIdentifier(id: String, name: String)

class MiscQueries(dynclient: DynamicsClient, m: MetadataCache, concurrency: Int = 4, verbosity: Int = 0)
  (implicit ec: ExecutionContext) {

  /** Get application uniquename -> id for creating URLs. */
  def getAppMapping(): IO[js.Object] = {
    val q = QuerySpec(
      select = Seq("name", "uniquename","appmoduleid")
    )
    dynclient.getList[AppModule](q.url("appmodules"))
      .map{ apps =>
        apps.map(a => (a.uniquename.get, a.appmoduleid.get)).toMap.toJSDictionary.asJsObj
      }
  }

    /**
   * Fetch entities (from record2) that match the specified record2 entity
   * logical names and roles.
   * @return record2 derived fat tuples (entity name, entity object type code, role name, role id, entity id)
   * @todo fromEntitySet is not technically needed for the query if we have an id
   */
  def entitiesFromConnectionsUsingNames(fromEntityId: apps.Id,
    toEntityNames: Traversable[String] = Nil, toRoleNames: Traversable[String] = Nil): IO[Seq[RelatedEntity]] = {
    val params = for {
      codes <- toEntityNames.map(m.objectTypeCode(_)).toList.sequence
      roles <- toRoleNames.map(m.connectionRole(_).map(_.map(_.connectionroleid))).toList.sequence
    } yield (codes, roles)

    params.flatMap{ case (codes, roles) =>
      entitiesFromConnections(fromEntityId,
        codes.collect{case Some(c) => c },
        roles.collect{case Some(r) => apps.Id(r)})
    }
  }

  def entitiesFromConnections(fromEntityId: apps.Id,
    toCodes: Traversable[Int] = Nil, toRoleIds: Traversable[apps.Id] = Nil): IO[Seq[RelatedEntity]] = {
    val codesq =
      if(!toCodes.isEmpty) "and " + In("record2objecttypecode", toCodes.map(_.toString))
      else ""
    val rolesq =
      if(!toRoleIds.isEmpty) "and " + In("record2roleid", toRoleIds.map(_.asString)) // not _record2roleid_value!
      else ""
    val q = QuerySpec(
      filter=Some(s"""_record1id_value eq $fromEntityId $codesq $rolesq"""),
    )
    dynclient.getListStream[ConnectionJs](q.url("connections"))
      .map { c =>
        m.entitySetName(c._record2id_entityname).map(_.map{ esname =>
          RelatedEntity(
            c._record2id_entityname, esname, c.record2objecttypecode,
            c._record2roleid_value_fv, apps.Id(c._record2roleid_value),
            apps.Id(c._record2id_value)
          )})
      }
      .map(Stream.eval(_))
      .join(concurrency)
      .collect{ case Some(e) => e }
      .compile.toVector
  }

  /**
   * Return systemmusers that have systemuser connections to the specified
   * entity. record1=entity, record2=sysemusers connected via he record2roleids
   * list.
   */
  def systemusersFromConnections(entitySet: String, entityId: apps.Id, record2roleids: Seq[String]): IO[Seq[SystemuserJS]] = {
    val q = QuerySpec(
      filter=Some(s"""_record1id_value eq $entityId"""),
      expand=Seq(Expand("record2_systemuser"))
    )
    dynclient.getList[ConnectionJs](q.url("connections"))
      .map{ _.map { c =>
        if(c.asDyn.record2id_systemuser.toTruthy) c.asDyn.record2id_systemuser.toNonNullOption[SystemuserJS]
        else None
      }.collect{ case Some(id) => id }}
  }

  /** Fetch email address from dynamics systemuser record. */
  def fetchEmailAddressFromId(id: apps.Id): IO[UPN] = {
    fetchSystemuser(id).map(user => UPN(user.internalemailaddress))
  }

  /** Fetch a system user by id. */
  def fetchSystemuser(id: apps.Id): IO[SystemuserJS] =
    dynclient.getOneWithKey[SystemuserJS]("systemusers", id.asString)

  def fetchEmailableSystemuser(id: apps.Id): IO[Option[SystemuserJS]] =
    fetchSystemuser(id)
      .map { user =>
        //if(user.isemailaddressapprovedbyo365admin && !user.isdisabled) Some(user)
        if(!user.isdisabled && user.islicensed) Some(user)
        else Option.empty
      }

  /**
   * Given an entity id, find its owner. If the owner is a team,
   * retrieve those systemusers. If errors occur along the way,
   * ignore them and return Nil as appropriate.
   */
//  def fetchOwnerUserOrTeam(esname: String, id: apps.Id): IO[Seq[String]] = {
//    dynclient.getOne()
//  }

  def fetchTeamSystemuserIds(teamId: apps.Id): IO[Seq[String]] = {
    val q = QuerySpec(
      select=Seq("systemuserid"),
      properties=Seq(NavProperty("teammembership_association"))
    )
    dynclient.getOne[js.Array[SystemuserJS]](q.url("teams", Some(teamId.asString)))(
      ValueArrayDecoder[SystemuserJS])
      .map(_.map(_.systemuserid).toSeq)
  }

}
