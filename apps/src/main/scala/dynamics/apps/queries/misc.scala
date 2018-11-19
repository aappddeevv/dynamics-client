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
import dynamics.client.common.{Id => EID}

/** An entity potentially related through a role. */
case class RelatedEntity(ename: String,
                         esname: String,
                         objectTypeCode: Int,
                         roleName: String,
                         roleId: EID,
                         id: EID)

/**
  * Used when we need to print out information about a record. Think of this as
  * a generic annotation.
  */
case class FriendlyIdentifier(id: String, name: String)

class MiscQueries(dynclient: DynamicsClient, m: MetadataCache, concurrency: Int = 4, verbosity: Int = 0)(
    implicit ec: ExecutionContext) {

  /** Get application uniquename -> id for creating URLs. */
  def getAppMapping(): IO[js.Object] = {
    val q = QuerySpec(
      select = Seq("name", "uniquename", "appmoduleid")
    )
    dynclient
      .getList[AppModule](q.url("appmodules"))
      .map { apps =>
        apps.map(a => (a.uniquename.get, a.appmoduleid.get)).toMap.toJSDictionary.asJsObj
      }
  }

  /**
    * Fetch entities (from record2) that match the specified record2 entity
    * logical names and roles. This just translates logical names into object
    * type codes then calls `entitiesFromConnections`.
    * @return record2 derived fat tuples (entity name, entity object type code, role name, role id, entity id)
    */
  def entitiesFromConnectionsUsingNames(fromEntityId: EID,
                                        toEntityNames: Traversable[String] = Nil,
                                        toRoleNames: Traversable[String] = Nil): IO[Seq[RelatedEntity]] = {
    val params = for {
      codes <- toEntityNames.map(m.objectTypeCode(_)).toList.sequence
      roles <- toRoleNames.map(m.connectionRole(_).map(_.map(_.connectionroleid))).toList.sequence
    } yield (codes, roles)

    params.flatMap {
      case (codes, roles) =>
        entitiesFromConnections(fromEntityId, codes.collect { case Some(c) => c }, roles.collect {
          case Some(r)                                                     => dynamics.client.common.Id(r)
        })
    }
  }

  /**
   * Given a "from" entity, return "to" entities that are in the specified
   * roles and have a specific object type code (yes the number since
   * connections store numbers and not logical names).
   */
  def entitiesFromConnections(fromEntityId: EID,
                              toCodes: Traversable[Int] = Nil,
                              toRoleIds: Traversable[EID] = Nil): IO[Seq[RelatedEntity]] = {
    val codesq =
      if (!toCodes.isEmpty) "and " + In("record2objecttypecode", toCodes.map(_.toString))
      else ""
    val rolesq =
      if (!toRoleIds.isEmpty) "and " + In("record2roleid", toRoleIds.map(_.asString)) // not _record2roleid_value!
      else ""
    val q = QuerySpec(
      filter = Some(s"""_record1id_value eq $fromEntityId $codesq $rolesq"""),
    )
    dynclient
      .getListStream[ConnectionJs](q.url("connections"))
      .map { c =>
        m.entitySetName(c._record2id_entityname)
          .map(_.map { esname =>
            RelatedEntity(
              c._record2id_entityname,
              esname,
              c.record2objecttypecode,
              c._record2roleid_value_fv,
              dynamics.client.common.Id(c._record2roleid_value),
              dynamics.client.common.Id(c._record2id_value)
            )
          })
      }
      .map(Stream.eval(_))
      .join(concurrency)
      .collect { case Some(e) => e }
      .compile
      .toVector
  }

  /**
    * Given a "from" entity return systemusers that are on the "to" side of the
    * connection. record1=entity, record2=sysemusers connected via he
    * record2roleids list.
    */
  def systemusersFromConnections(entitySet: String,
                                 entityId: EID,
                                 record2roleids: Seq[String]): IO[Seq[SystemuserJS]] = {
    val q = QuerySpec(
      filter = Some(s"""_record1id_value eq $entityId"""),
      expand = Seq(Expand("record2_systemuser"))
    )
    dynclient
      .getList[ConnectionJs](q.url("connections"))
      .map {
        _.map { c =>
          if (c.asDyn.record2id_systemuser.toTruthy) c.asDyn.record2id_systemuser.toNonNullOption[SystemuserJS]
          else None
        }.collect { case Some(id) => id }
      }
  }

  /** Fetch email address from dynamics systemuser record. */
  def fetchEmailAddressFromId(id: EID): IO[UPN] = {
    fetchSystemuser(id).map(user => UPN(user.internalemailaddress))
  }

  /** Fetch a system user by id. */
  def fetchSystemuser(id: EID): IO[SystemuserJS] =
    dynclient.getOneWithKey[SystemuserJS]("systemusers", id.asString)

  def fetchEmailableSystemuser(id: dynamics.client.common.Id): IO[Option[SystemuserJS]] =
    fetchSystemuser(id)
      .map { user =>
        //if(user.isemailaddressapprovedbyo365admin && !user.isdisabled) Some(user)
        if (!user.isdisabled && user.islicensed) Some(user)
        else Option.empty
      }

  /** Expand the team id to a list of systemuser ids. */
  def fetchTeamSystemuserIds(teamId: EID): IO[Seq[String]] = {
    val q = QuerySpec(
      select = Seq("systemuserid"),
      properties = Seq(NavProperty("teammembership_association"))
    )
    dynclient
      .getOne[js.Array[SystemuserJS]](q.url("teams", Some(teamId.asString)))(ValueArrayDecoder[SystemuserJS])
      .map(_.map(_.systemuserid).toSeq)
  }

  /** Given a dynamics entity look at the "owning" attributes to find all
   * systemusers. This expands out a team if the entity is owned by a team.  If
   * neither of these are filled out, return an empty list. If those attributes
   * are not found it tries to interpret _ownerid_value if the lookup logical
   * name odata attribute is found. IF assumeUser is true, a plain  _ownerid_value
   * without the lookup logical name is assumed to be a systemuser.
   */
  def ownerIdsFrom(obj: js.Object, assumeUser: Boolean = false): IO[Seq[String]] = {
    val tmp = obj.asInstanceOf[Owned]
    (tmp._owninguser_value.map(id => IO.pure(Seq(id))).toOption orElse
    tmp._owningteam_value.map(tid => fetchTeamSystemuserIds(EID(tid))).toOption orElse
      ((tmp._ownerid_value.toOption, tmp._ownerid_value_lln.toOption) match {
        case (Some(id), Some(lln)) if lln == "systemuser" =>
          Some(IO.pure(Seq(id)))
        case (Some(id), Some(lln)) if lln == "team" =>
          Some(fetchTeamSystemuserIds(EID(id)))
        case (Some(id), _) if assumeUser =>
          Some(IO.pure(Seq(id)))
        case _ =>
          Some(IO.pure(Seq.empty))
      })).getOrElse(IO.pure(Seq.empty))
  }

}
