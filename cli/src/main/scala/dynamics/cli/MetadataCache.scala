// Copyright (c) 2017 The Trapelo Group LLC
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
import scala.concurrent.duration._
import fs2._
import cats._
import cats.data._
import cats.implicits._
import cats.effect._

import io.scalajs.npm.chalk._

import MonadlessIO._
import dynamics.http._
import dynamics.client._
import dynamics.client.implicits._
import dynamics.common.implicits._
import dynamics.http.implicits._

class EntityDefinition(
    val PrimaryNameAttribute: js.UndefOr[String] = js.undefined,
    val LogicalName: js.UndefOr[String] = js.undefined,
    val SchemaName: js.UndefOr[String] = js.undefined,
    val PrimaryIdAttribute: js.UndefOr[String] = js.undefined,
    val LogicalCollectionName: js.UndefOr[String] = js.undefined,
    val CollectionSchemaName: js.UndefOr[String] = js.undefined,
    val EntitySetName: js.UndefOr[String] = js.undefined,
    val IsLogical: js.UndefOr[Boolean] = js.undefined,
    val MetadataId: js.UndefOr[String] = js.undefined,
    val IsCustomEntity: js.UndefOr[Boolean] = js.undefined,
    val IsActivity: js.UndefOr[Boolean] = js.undefined,
    val IsActivityParty: js.UndefOr[Boolean] = js.undefined,
    val Description: js.UndefOr[String] = js.undefined,
    val ObjectTypeCode: js.UndefOr[Int] = js.undefined
) extends js.Object

case class Property(name: String, edmType: String)

/** Full response. Note missing Color, HasChanged */
trait OptionSetItem extends js.Object {
  val Value: Int
  val Label: LocalizedInfo
  val Description: LocalizedInfo
  val IsManaged: Boolean
}

/** This is the object returned from the OptionSet or GlobalOptionSet attribuet property. */
trait OptionSetResponse extends js.Object {
  val Options: js.Array[OptionSetItem]
}

/** When asking for both local or global options sets. */
trait LocalOrGlobalOptionSetsResponse extends js.Object {
  val OptionSet: UndefOr[OptionSetResponse]       = js.undefined
  val GlobalOptionSet: UndefOr[OptionSetResponse] = js.undefined
}

case class OptionValue(label: String, value: Int)

/**
  * Simple metadata cache for CRM metadata. The cache
  * is mutable and retrieves remote data on demand. All calls
  * to obtain metadata could be effectul.
  */
class MetadataCache(val context: DynamicsContext) {

  import context._
  import metadata._
  import dynamics.common.syntax.all._
  import LocalizedHelpers._

  //import scalacache._
  //implicit val cache = ScalaCache(new ScalaCacheNodeCache(new NodeCache()))
  val ucache = new NodeCache()

  //val xmlDoc = new XmlDocument(if(csdl=="") "<Nothing/>" else csdl)
  //implicit val dec = EntityDecoder.JsObjectDecoder[js.Object]

  //println("CSDL: " + Utils.render(xmlDoc))

  val entityNameToCollectionName = Map[String, String]()
  val collectionNameToEntityName = Map[String, String]()

  /*
   def typeOf(entity: String, prop: String): Option[Property] = {
   var node = xmlDoc.descendantWithPath("EntityType")
   println(s"node = ${Utils.render(node)}")
   None
   }
   */

  protected def toEntityDescription(j: js.Object): EntityDescription = {
    val e = j.asDyn
    EntityDescription(
      Description = "",
      //findByLCID(1033, e.Description.asJsObjSub[LocalizedInfo]).map(_.Label).getOrElse(""),
      LogicalName = e.LogicalName.asString,
      LogicalCollectionName = e.LogicalCollectionName.asString,
      PrimaryId = e.PrimaryIdAttribute.asString,
      PrimaryNameAttribute = "", //e.PrimaryNameAttribute.asString,
      DisplayName = "", //e.DisplayName.asString,
      DisplayCollectionName = "",
      //findByLCID(1033, e.DisplayCollectionName.asJsObjSub[LocalizedInfo]).map(_.Label).getOrElse(""),
      EntitySetName = e.EntitySetName.asString,
      IsActivity = false, //e.IsActivity.asBoolean,
      MetadataId = e.MetadataId.asString,
      ObjectTypeCode = -1 //e.ObjectTypeCode.asInt
    )
  }

  /*
   protected def toAttributeDescription(j: js.Object): AttributeDescription = {
   val e = j.asDyn
   /*
   AttributeDescription(
   SchemaName = "",
   LogicalName = "",
   IsValidForRead = true,
   IsPrimaryId = false,
   AttributeOf: Option
   )
   */
   null
   }
   */

  def getEntityList(): IO[Seq[EntityDescription]] = {
    val fields = Seq(
      "Description",
      "LogicalName",
      "LogicalCollectionName",
      "MetadataId",
      "PrimaryIdAttribute",
      "PrimaryNameAttribute",
      "DisplayName",
      "DisplayCollectionName",
      "EntitySetName",
      "IsActivity"
    )
    val q = QuerySpec(filter = Some("IsPrivate eq false"), select = fields)
    dynclient.getList[js.Object](q.url("EntityDefinitions")).map {
      _ map { ed =>
        toEntityDescription(ed)
      }
    }
  }

  def getEntityDescription(entitySet: String): IO[Option[EntityDescription]] = {
    //println(s"getEntityDescription: $entitySet")
    val q = QuerySpec(filter = Some(s"EntitySetName eq '$entitySet'"))
    dynclient.getOne[js.Object](q.url("EntityDefinitions"))(ValueWrapper[js.Object]).attempt.map {
      _ match {
        case Right(v) =>
          //println(s"HERE!: ${PrettyJson.render(v)}")
          Some(toEntityDescription(v))
        case _ => None
      }
    }
  }

  /** Semi-efficient lookup. */
  def getAttributeMetadataId(entitySet: String, attribute: String): IO[Option[String]] = {
    val eq = QuerySpec(filter = Some(s"EntitySetName eq '$entitySet'"), select = Seq("MetadataId"))
    dynclient.getOne[js.Object](eq.url("EntityDefinitions"))(ValueWrapper[js.Object]).flatMap { obj =>
      val id = obj.asDyn.MetadataId.asString
      val aq = QuerySpec(select = Seq("MetadataId"))
        .withExpand(Expand("Attributes", filter = Some(s"LogicalName eq '$attribute'")))
      dynclient.getOne[js.Object](aq.url("EntityDefinitions", Some(id))).map { aobj =>
        //println(s"${PrettyJson.render(aobj)}")
        aobj.asDyn.Attributes.asJsArray[js.Object](0).asDyn.MetadataId.asUndefOr[String].toOption
      }
    }
  }

  private def govKey(e: String, a: String) = "getOptionValues-" + e + "-" + a

  /** Only gets them if they are local to the entity...not global option sets. */
  def getOptionValues(entitySet: String, attribute: String): IO[Seq[OptionValue]] = {
    val k = govKey(entitySet, attribute)

    val valueOpt: IO[Option[Seq[OptionValue]]] = IO { ucache.get[Seq[OptionValue]](k).toOption }

    val getTask = lift {
      val eid = unlift(getEntityDescription(entitySet)).map(_.MetadataId)
      val aid = unlift(getAttributeMetadataId(entitySet, attribute))
      //println(s"e-a key: $eid, $aid")
      val q = QuerySpec(properties =
                          Seq(NavProperty("Attributes", aid, Some("Microsoft.Dynamics.CRM.PicklistAttributeMetadata")),
                              NavProperty("OptionSet")),
                        select = Seq("Options"))
      val url = q.url("EntityDefinitions", eid)
      unlift(dynclient.getOne[OptionSetResponse](url).map { resp =>
        //println(s"attr optionset response: ${PrettyJson.render(resp)}")
        val result = resp.Options.map { oitem =>
          val label = LocalizedHelpers
            .findByLCID(1033, oitem.Label)
            .map(_.Label)
            .getOrElse(throw new IllegalArgumentException(
              s"Unable to find localized label for $entitySet.$attribute OptionSet."))
          OptionValue(label, oitem.Value)
        }.seq
        ucache.set(k, result) // stick in cache
        result
      })
    }
    valueOpt.flatMap(_.map(IO.pure).getOrElse(getTask))
  }

  /** Given an entity set name, return the entity definition. */
  def getEntityDefinition2(ename: String): DecodeResult[EntityDefinition] = {
    val q = QuerySpec(filter = Option(s"EntitySetName eq '$ename'"))
    val result = lift {
      val values = unlift(dynclient.getList[EntityDefinition](q.url("EntityDefinitions")))
      if (values.size != 1)
        Left(OnlyOneExpected(s"EntityDefinition for entity set name $ename"))
      else
        Right(values(0))
    }
    DecodeResult(result)
  }

  /** Get entity definition using logical name as the key. Use the entity logical name not the entity set name. */
  def getEntityDefinition3(ename: String): IO[EntityDescription] = {
    /*
GET [Organization URI]/api/data/v8.2/
EntityDefinitions(LogicalName='account')/Attributes(LogicalName='accountcategorycode')/Microsoft.Dynamics.CRM.PicklistAttributeMetadata?$select=LogicalName&$expand=OptionSet($select=Options),GlobalOptionSet($select=Options)
     */

    val key = AltId("LogicalName", s"'$ename'")
    dynclient.getOneWithKey[EntityDefinition]("EntityDefinitions", key).map(toEntityDescription)
  }

  /** Given an entity set name and an attribute name, return the attribute type.
    */
  def getAttributeTypeCode(ename: String, aname: String): IO[AttributeTypeCode] = {
    getEntityDefinition2(ename).toIO.flatMap { ed =>
      val q =
        QuerySpec(select = Seq("LogicalName", "EntitySetName"),
                  expand =
                    Seq(Expand("Attributes", select = Seq("AttributeType"), filter = Some(s"LogicalName eq '$aname'"))))
      val opts = DynamicsOptions(prefers = OData.PreferOptions(includeFormattedValues = Some(true)))
      dynclient.getOne[String](q.url("EntityDefinitions", Some(ed.MetadataId.get)), opts).map { ad =>
        println(s"metadata get: $ad")
        metadata.AttributeTypeCode.Integer
      }
    }
  }

  /** Get object type code for a specific ename.
    *
    * To get all of them quickly.
    * A faster query is [org Url]/api/data/v8.2/EntityDefinitions?$select=LogicalName,ObjectTypeCode
    *
    * Customer entities: $filter=ObjectTypeCode gt 9999
    */
  def getObjectTypeCode(ename: String): IO[Option[Int]] = {
    getEntityDefinition2(ename).toIO.map { ed =>
      ed.ObjectTypeCode.toOption
    }
  }

}

@js.native
@JSImport("shorthash", JSImport.Namespace)
object ShortHash extends js.Object {
  def unique(in: String): String = js.native
}

/** Cache the metadata string returned from CRM. CSDL is cached across runs. */
case class CSDLFileCache(name: String, context: DynamicsContext, ignore: Boolean = false, location: String = ".")
    extends FileCache(Utils.pathjoin(location, ShortHash.unique(name) + ".csdl.cache"), ignore) {

  protected def getContent() = (new MetadataActions(context)).getCSDL()

}
