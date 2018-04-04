// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package cli

import dynamics.common._

import scala.language._
import scala.util.control.Exception._
import scala.concurrent.duration._
import scala.util._
import scala.util.matching.Regex
import collection._

/**
  * Metadata objects retrieveable from the CRM server.
  * Metadata is converted to a scala-first format.
  *
  *  Procesing only grabs the user localized versions
  *  of localized strings.
  *
  *  TODO: Allow language selection on localized strings.
  */
object metadata {

  /** Required level in attribute metadata. */
  sealed trait RequiredLevel

  /** No requirement. */
  case object NoRequirement extends RequiredLevel

  /** System required */
  case object SystemRequired extends RequiredLevel

  /** Application required. */
  case object ApplicationRequired extends RequiredLevel

  /** Convert parsed string to RequiredLevel. */
  def toRequiredLevel(n: String) = n match {
    case "None"                => NoRequirement
    case "SystemRequired"      => SystemRequired
    case "ApplicationRequired" => ApplicationRequired
  }

  /**
    * Attribute metadata.
    */
  sealed trait Attribute {
    def SchemaName: String
    def LogicalName: String
    def AttributeType: AttributeTypeCode
    def IsValidForRead: Boolean // can be read in a retrieve
    def IsPrimaryId: Boolean
    def IsLogical: Boolean // whether stored in a different table
    def AttributeOf: Option[String]
    def ColumnNumber: Int
    def MetadataId: String
    def EntityLogicalName: String
  }

  case class BasicAttribute(
      SchemaName: String,
      LogicalName: String,
      IsValidForRead: Boolean,
      IsPrimaryId: Boolean,
      IsLogical: Boolean, // whether stored in a different table
      AttributeOf: Option[String] = None,
      AttributeType: AttributeTypeCode,
      ColumnNumber: Int = -1,
      MetadataId: String,
      EntityLogicalName: String, // link back to Entity
      DisplayName: String
  ) extends Attribute

  /*
  case class DateTimeAttribute(attributeType: String,
    schemaName: String,
    logicalName: String,
    isValidForRead: Boolean,
    isPrimaryId: Boolean,
    isLogical: Boolean, // whether stored in a different table
    attributeOf: Option[String] = None,
    columnNumber: Int = -1,
    metadataId: String,
    entityLogicalName: String) extends Attribute

  case class MoneyAttribute(attributeType: String,
    schemaName: String,
    logicalName: String,
    isValidForRead: Boolean,
    isPrimaryId: Boolean,
    isLogical: Boolean, // whether stored in a different table
    attributeOf: Option[String] = None,
    columnNumber: Int = -1,
    metadataId: String,
    entityLogicalName: String) extends Attribute

  case class DecimalAttribute(attributeType: String,
    schemaName: String,
    logicalName: String,
    isValidForRead: Boolean,
    isPrimaryId: Boolean,
    isLogical: Boolean, // whether stored in a different table
    attributeOf: Option[String] = None,
    columnNumber: Int = -1,
    metadataId: String,
    entityLogicalName: String,
    precision: Option[Int]) extends Attribute

  case class BooleanAttribute(attributeType: String,
    schemaName: String,
    logicalName: String,
    isValidForRead: Boolean,
    isPrimaryId: Boolean,
    isLogical: Boolean, // whether stored in a different table
    attributeOf: Option[String] = None,
    columnNumber: Int = -1,
    metadataId: String,
    entityLogicalName: String) extends Attribute

  case class IntegerAttribute(attributeType: String,
    schemaName: String,
    logicalName: String,
    isValidForRead: Boolean,
    isPrimaryId: Boolean,
    isLogical: Boolean, // whether stored in a different table
    attributeOf: Option[String] = None,
    columnNumber: Int = -1,
    metadataId: String,
    entityLogicalName: String) extends Attribute

  case class DoubleAttribute(attributeType: String,
    schemaName: String,
    logicalName: String,
    isValidForRead: Boolean,
    isPrimaryId: Boolean,
    isLogical: Boolean, // whether stored in a different table
    attributeOf: Option[String] = None,
    columnNumber: Int = -1,
    metadataId: String,
    entityLogicalName: String,
    minValue: Double = 0,
    maxValue: Double = 0) extends Attribute

  case class BigIntAttribute(attributeType: String,
    schemaName: String,
    logicalName: String,
    isValidForRead: Boolean,
    isPrimaryId: Boolean,
    isLogical: Boolean, // whether stored in a different table
    attributeOf: Option[String] = None,
    columnNumber: Int = -1,
    metadataId: String,
    entityLogicalName: String) extends Attribute

  case class StringAttribute(attributeType: String,
    schemaName: String,
    logicalName: String,
    isValidForRead: Boolean,
    isPrimaryId: Boolean,
    isLogical: Boolean, // whether stored in a different table
    attributeOf: Option[String] = None,
    columnNumber: Int = -1,
    metadataId: String,
    entityLogicalName: String,
    minLength: Int = 0,
    maxLength: Int,
    format: Option[String]) extends Attribute

  case class MemoAttribute(attributeType: String,
    schemaName: String,
    logicalName: String,
    isValidForRead: Boolean,
    isPrimaryId: Boolean,
    isLogical: Boolean,
    attributeOf: Option[String] = None,
    columnNumber: Int = -1,
    metadataId: String,
    entityLogicalName: String,
    maxLength: Int,
    format: Option[String]) extends Attribute

  case class EntityNameAttribute(attributeType: String,
    schemaName: String,
    logicalName: String,
    isValidForRead: Boolean,
    isPrimaryId: Boolean,
    isLogical: Boolean, // whether stored in a different table
    attributeOf: Option[String] = None,
    columnNumber: Int = -1,
    metadataId: String,
    entityLogicalName: String,
    format: Option[String]) extends Attribute

  case class LookupAttribute(attributeType: String,
    schemaName: String,
    logicalName: String,
    isValidForRead: Boolean,
    isPrimaryId: Boolean,
    isLogical: Boolean, // whether stored in a different table
    attributeOf: Option[String] = None,
    columnNumber: Int = -1,
    metadataId: String,
    entityLogicalName: String,
    targets: Seq[String]) extends Attribute

  case class StateAttribute(attributeType: String,
    schemaName: String,
    logicalName: String,
    isValidForRead: Boolean,
    isPrimaryId: Boolean,
    isLogical: Boolean, // whether stored in a different table
    attributeOf: Option[String] = None,
    columnNumber: Int = -1,
    metadataId: String,
    entityLogicalName: String,
    options: OptionSet) extends Attribute

  case class StatusAttribute(attributeType: String,
    schemaName: String,
    logicalName: String,
    isValidForRead: Boolean,
    isPrimaryId: Boolean,
    isLogical: Boolean, // whether stored in a different table
    attributeOf: Option[String] = None,
    columnNumber: Int = -1,
    metadataId: String,
    entityLogicalName: String,
    options: OptionSet) extends Attribute

  case class PicklistAttribute(attributeType: String,
    schemaName: String,
    logicalName: String,
    isValidForRead: Boolean,
    isPrimaryId: Boolean,
    isLogical: Boolean, // whether stored in a different table
    attributeOf: Option[String] = None,
    columnNumber: Int = -1,
    metadataId: String,
    entityLogicalName: String,
    options: OptionSet) extends Attribute

  sealed trait DateTimeBehavior
  case object DateOnly extends DateTimeBehavior
  case object TimeZoneIndependent extends DateTimeBehavior
  case object USerlocal extends DateTimeBehavior

  /** An Option in an OptionSet */
  case class OptionMetadata(label: String, value: String)

  /** An option list for a PickListAttribute. */
  case class OptionSet(name: String,
    displayName: String,
    description: String,
    isGlobal: Boolean,
    optionSetType: String,
    options: Seq[OptionMetadata],
    id: String)

  /**
   * The referenced/referencing names are logical names.
   */
  case class Relationship(schemaName: String,
    referencedAttribute: String,
    referencedEntity: String,
    referencingAttribute: String,
    referencedEntityNavigationPropertyName: String,
    referencingEntity: String,
    referencingEntityNavigationPropertyName: String)

   */
  /**
    * Entity metadata.
    */
  case class EntityDescription(Description: String,
                               LogicalName: String,
                               PrimaryId: String,
                               PrimaryNameAttribute: String,
                               DisplayName: String,
                               DisplayCollectionName: String,
                               LogicalCollectionName: String,
                               EntitySetName: String,
                               IsActivity: Boolean,
                               MetadataId: String,
                               ObjectTypeCode: Int,
                               /*      OneToMany: Seq[Relationship] = Nil,
      ManyToOne: Seq[Relationship] = Nil,
      ManyToMany: Seq[Relationship] = Nil,*/
                               Attributes: Seq[Attribute] = Nil) {

    /**
      * List of attributes that can be retrieved in a query/fetch request.
      * This is different than the metadata concept IsRetrievable which is
      * for internal use only.
      */
    //def retrievableAttributes = attributes.filter(a => !a.isLogical && a.isValidForRead)

  }

  //object UnknownEntityDescription extends EntityDescription("unkown", "unkown", "unknown")

  /** Overall CRM schema. */
  case class CRMSchema(entities: Seq[EntityDescription])

  /*
  /** Find a primary id logical name for a specific entity. Entity name is not case sensitive. */
  def primaryId(ename: String, schema: CRMSchema): Option[String] =
    for {
      e <- schema.entities.find(_.logicalName.trim.toUpperCase == ename.trim.toUpperCase)
      //a <- e.attributes.find(_.isPrimaryId)
    } yield e.primaryId //yield a.logicalName
   */

  /**
    * Find an entity ignoring case in the entity's logical name.
    */
  /*
  def findEntity(ename: String, schema: CRMSchema) =
    schema.entities.find(_.logicalName.trim.toUpperCase == ename.trim.toUpperCase)
   */

  final case class AttributeTypeCode(code: Int, label: String)

  object AttributeTypeCode {

    def lookup(code: Int): AttributeTypeCode =
      Codes.get(code).getOrElse(AttributeTypeCode(code, "No description available."))

    private val Codes: Map[Int, AttributeTypeCode] = Map(
      0    -> AttributeTypeCode(0, "Boolean"),
      1    -> AttributeTypeCode(1, "Customer"),
      2    -> AttributeTypeCode(2, "DateTime"),
      3    -> AttributeTypeCode(3, "Decimal"),
      4    -> AttributeTypeCode(4, "Double"),
      5    -> AttributeTypeCode(5, "Integer"),
      6    -> AttributeTypeCode(6, "Lookup"),
      7    -> AttributeTypeCode(7, "Memo"),
      8    -> AttributeTypeCode(8, "Money"),
      9    -> AttributeTypeCode(9, "Owner"),
      10   -> AttributeTypeCode(10, "PartyList"),
      11   -> AttributeTypeCode(11, "Picklist"),
      12   -> AttributeTypeCode(12, "State"),
      13   -> AttributeTypeCode(13, "Status"),
      14   -> AttributeTypeCode(14, "String"),
      15   -> AttributeTypeCode(15, "Uniqueidentifier"),
      16 -> AttributeTypeCode(0x10, "CalendarRules"),      
      17 -> AttributeTypeCode(0x11, "Virtual"),
      18 -> AttributeTypeCode(0x12, "BigInt"),
      19 -> AttributeTypeCode(0x13, "ManagedProperty"),
      20   -> AttributeTypeCode(20, "EntityName"),      
    )

    val BigInt = Codes(0x12)
    /*
    case object Boolean extends AttributeTypeCode(0, "Boolean")
    case object CalendarRules extends AttributeTypeCode(0x10, "CalendarRules")
    case object Customer extends AttributeTypeCode(1, "Customer")
    case object DateTime extends AttributeTypeCode(2, "DateTime")
    case object Decimal extends AttributeTypeCode(3, "Decimal")
    case object Double extends AttributeTypeCode(4, "Double")
    case object EntityName extends AttributeTypeCode(20, "EntityName")
     */
    val Integer = Codes(5)
    /*
    case object Lookup extends AttributeTypeCode(6, "Lookup")
    case object ManagedProperty extends AttributeTypeCode(0x13, "ManagedProperty")
    case object Memo extends AttributeTypeCode(7, "Memo")
    case object Money extends AttributeTypeCode(8, "Money")
    case object Owner extends AttributeTypeCode(9, "Owner")
    case object PartyList extends AttributeTypeCode(10, "PartyList")
    case object Picklist extends AttributeTypeCode(11, "Picklist")
    case object State extends AttributeTypeCode(12, "State")
    case object Status extends AttributeTypeCode(13, "Status")
    case object String extends AttributeTypeCode(14, "String")
    case object Uniqueidentifier extends AttributeTypeCode(15, "Uniqueidentifier")
    case object Virtual extends AttributeTypeCode(0x11, "Virtual"))
   */

  }

}
