// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package client
package common

import dynamics.common._

import scala.scalajs.js
import js._
import js.annotation._
import io.scalajs.nodejs._
import fs2._
import js.JSConverters._
import cats._
import cats.data._
import cats.implicits._
import js.Dynamic.{literal => jsobj}
import MonadlessIO._
import cats.effect._

import dynamics.common.syntax.jsundefor._
import Utils._

/**
  * @see https://docs.microsoft.com/en-us/dynamics365/customer-engagement/web-api/localizedlabel?view=dynamics-ce-odata-9
  */
@js.native
trait LocalizedLabel extends js.Object {
  val Label: String               = js.native
  val MetadataId: String          = js.native
  val IsManaged: UndefOr[Boolean] = js.native
  val LanguageCode: Int           = js.native
}

/**
  * Typically a Label or Description.
  *
  * @see https://docs.microsoft.com/en-us/dynamics365/customer-engagement/web-api/label?view=dynamics-ce-odata-9
  */
@js.native
trait Label extends js.Object {
  val LocalizedLabels: UndefOr[js.Array[LocalizedLabel]] = js.native
  val UserLocalizedLabel: UndefOr[LocalizedLabel]        = js.native
}

object LocalizedHelpers {

  /** Get user localized label. If absent, use lcid, if absent, return None */
  def label(info: Label, lcid: Option[Int] = None): Option[String] =
    labelForUser(info) orElse lcid.flatMap(i => findByLCID(i, info)).map(_.Label)

  /** Return the label for the user localized label or None. */
  def labelForUser(info: Label): Option[String] =
    info.UserLocalizedLabel.toNonNullOption.map(_.Label)

  /**
    * Return the localized label (based on lcid) then the user localized label
    * then None.
    */
  def findByLCID(lcid: Int, info: Label): Option[LocalizedLabel] =
    info.LocalizedLabels.toOption.flatMap(_.find(_.LanguageCode == lcid)) orElse
      info.UserLocalizedLabel.toOption

    /** Make labels easier to use. */
  implicit class RichLabel(label: Label) {
    /** Obtain the label string from a Label or a default empty string.. */
    def label: String = LocalizedHelpers.label(label).getOrElse("")
    /** Obtain the label string from a Label and lcsid or fallback to user label. */
    def label(lcsid: Int): String = findByLCID(lcsid, label).map(_.Label).getOrElse("no label")
  }

}
