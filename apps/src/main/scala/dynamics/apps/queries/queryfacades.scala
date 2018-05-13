// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package apps
package queries

import scala.scalajs.js
import js._
import js.annotation._
import js.Dynamic.{literal => jsobj}
import JSConverters._

/** FetchXML source of entities. */
trait FetchXMLQuery extends js.Object {

  /** Entity set name. Required for fetch queries. */
  var entitySetName: js.UndefOr[String] = js.undefined

  /** fetchXml string in JSON string format. */
  var fetchXML: js.UndefOr[String] = js.undefined
}

/** OData source of entities. */
trait ODataQuery extends js.Object {

  /** OData query URL. */
  var odata: js.UndefOr[String] = js.undefined
}

@js.native
trait ConnectionJs extends js.Object {
  val name: String         = js.native
  val connectionid: String = js.native

  @JSName("_record1roleid_value@OData.Community.Display.V1.FormattedValue")
  val _record1roleid_value_for: String = js.native
  @JSName("_record1roleid_value@OData.Community.Display.V1.FormattedValue")
  val _record1roleid_value_fv: String = js.native
  val _record1roleid_value: String    = js.native

  @JSName("_record2roleid_value@OData.Community.Display.V1.FormattedValue")
  val _record2roleid_value_for: String = js.native
  @JSName("_record2roleid_value@OData.Community.Display.V1.FormattedValue")
  val _record2roleid_value_fv: String = js.native
  val _record2roleid_value: String    = js.native

  val _record1id_value: String = js.native
  val _record2id_value: String = js.native

  // entity logical name via the lookup logical name annotation option
  // capitalized name in record*objecttypecode@OData.Community.Display.V1.FormattedValue
  @JSName("_record1id_value@Microsoft.Dynamics.CRM.lookuplogicalname")
  val _record1id_entityname: String = js.native
  @JSName("_record2id_value@Microsoft.Dynamics.CRM.lookuplogicalname")
  val _record2id_entityname: String = js.native

  val record1objecttypecode: Int = js.native
  val record2objecttypecode: Int = js.native

  val ismaster: Boolean = js.native
}
