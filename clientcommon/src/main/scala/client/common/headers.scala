// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package client
package common

import dynamics.http._

object headers {

  val FormattedValue               = "OData.Community.Display.V1.FormattedValue"
  val NextLink                     = "@odata.nextLink"

  //
  // these should definitely not be here
  //
  val AssociatedNavigationProperty = "Microsoft.Dynamics.CRM.associatednavigationproperty"
  val LookupLogicalName            = "Microsoft.Dynamics.CRM.lookuplogicalname"

  /** Create an attribute name with modifier. */
  def attr(p: String, mod: String) = p + "@" + mod

  def getBasicHeaders(): HttpHeaders =
    HttpHeaders("OData-Version"    -> "4.0",
                "OData-MaxVersion" -> "4.0",
                "Cache-Control"    -> "no-cache",
                "If-None-Match"    -> "null") ++
      AcceptHeader ++
      ContentTypeJson

  val ContentTypeJson = HttpHeaders("Content-Type" -> "application/json; charset=utf-8")
  val AcceptHeader    = HttpHeaders("Accept"       -> "application/json")

    /** Default includes everything. */
  case class PreferOptions(maxPageSize: Option[Int] = None,
                           includeRepresentation: Option[Boolean] = None,
                           includeFormattedValues: Option[Boolean] = None,
                           includeLookupLogicalNames: Option[Boolean] = None,
                           includeAssociatedNavigationProperties: Option[Boolean] = None)

  /** Quiet prefer options. */
  val QuietPreferOptions = PreferOptions(
    includeRepresentation = Some(false),
    includeFormattedValues = Some(false),
    includeLookupLogicalNames = Some(false),
    includeAssociatedNavigationProperties = Some(false)
  )

  /** Adds everything to be returned. */
  val NoisyPreferOptions = PreferOptions(
    includeRepresentation = Some(true),
    includeFormattedValues = Some(true),
    includeLookupLogicalNames = Some(true),
    includeAssociatedNavigationProperties = Some(true)
  )

  /** Default Prefer options, noisy. */
  val DefaultPreferOptions = NoisyPreferOptions

  /** Return formatted values. */
  val FormattedValues = PreferOptions(includeFormattedValues = Some(true))

  /** Renders value side of Prefer header. */
  def render(popts: PreferOptions): Option[String] = {
    val opts = collection.mutable.ListBuffer[Option[String]]()

    opts += popts.maxPageSize.map(x => s"odata.maxpagesize=$x")
    opts += popts.includeRepresentation.flatMap {
      _ match {
        case true => Option("return=representation")
        case _    => None
      }
    }

    val preferExtra = Seq(
      popts.includeFormattedValues.flatMap(f => if (f) Some(FormattedValue) else None),
      popts.includeLookupLogicalNames.flatMap(f => if (f) Some(LookupLogicalName) else None),
      popts.includeAssociatedNavigationProperties.flatMap(f => if (f) Some(AssociatedNavigationProperty) else None)
    ).collect { case Some(x) => x }

    if (preferExtra.size == 3) opts += Some("odata.include-annotations=\"*\"");
    else if (preferExtra.size != 0) opts += Some("odata.include-annotations=\"" + preferExtra.mkString(",") + "\"")

    val str = opts.collect { case Some(x) => x }.mkString(",")
    if (str == "") None
    else Some(str)
  }

}
