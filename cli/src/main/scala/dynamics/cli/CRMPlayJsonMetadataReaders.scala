// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package cli

import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._

object JsonPlayMetadatReaders {

  val schemaNameReader     = (__ \ "SchemaName").read[String]
  val logicalNameReader    = (__ \ "LogicalName").read[String]
  val attributeTypeReader  = (__ \ "AttributeType").read[String]
  val isValidForReadReader = (__ \ "IsValidForRead").read[Boolean]
  val isPrimaryIdReader    = (__ \ "IsPrimaryId").read[Boolean]
  val isLogicalReader      = (__ \ "IsLogical").read[Boolean]
  val attributeOfReader    = (__ \ "AttributeOf").readNullable[String].filterNot(_.isEmpty)
  //val columnNumberReader = (__ \ "ColumnNumber").read[Int].default(-1)
  val metadataIdReader        = (__ \ "MetadataId").read[String]
  val entityLogicalNameReader = (__ \ "EntityLogicalName").read[String]
  //val displayNameReader = (__ \ "DisplayName" \ "UserLocalizedLabel").read[String].optional
  //val descriptionNameReaer = (__ \ "Description" \ "UserLocalizedLabel").read[String].optional
  val minValueD = (__ \ "MinValue").read[Double]
  val maxValueD = (__ \ "MaxValue").read[Double]
  //val precisionReader = (__ \ "Precision").read[Int].optional

  //val stringArray: XmlReader[Seq[String]] = (__ \\ "string").read(seq[String])

  val basicAttributeReader =
    schemaNameReader and
      logicalNameReader and
      isValidForReadReader and
      isPrimaryIdReader and
      isLogicalReader and
      //attributeOfReader and
      //columnNumberReader and
      metadataIdReader and
      entityLogicalNameReader

  val stringAttributeReader = basicAttributeReader and
    logicalNameReader

  val integerAttributeReader = basicAttributeReader and
    logicalNameReader

}
