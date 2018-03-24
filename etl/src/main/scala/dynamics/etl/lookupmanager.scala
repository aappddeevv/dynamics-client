// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package etl

import scala.scalajs.js
import js._

import client._

case class TextLookupOptions(
  /** Typically the entity's PK, automatically calculated. */
  lookedUpAttribute: Option[String] = None,

  /** Whether the returned value is in "@odata.bind" syntax. */
  bind: Boolean = true,

  /** Language code to use. Default is user specific. */
  lcid: Option[Int] = None
)

case class OptionSetLookupOptions (
  /** Language code to use. Default is user specific. */
  lcid: Option[Int] = None
)

trait LookupManager {
  def makeTextLookup(entityName: String, opts: TextLookupOptions): LookupText
  def makeOptionSetLookup(entity: Option[(String, String)] = None): LookupOption
}

/**
 * Create smart lookup functions that can take into acount caching, etc.
 */
trait LookupManagerImpl extends LookupManager {

  /**
   * Create a lookup function for the specified entity suitable to use on a json payload. The default lookup
   * strategy obtains the id.
   * @return Pair with lhs, rhs. lhs may carry a "attribute@odata.bind" value and rhs may be "/entitySetName(id)".
   */
  def makeTextLookup(entityName: String, opts: TextLookupOptions): LookupText = {
    (value: String) => {
      Right(("pk", "/blah(id)"))
    }
  }

  /**
   * Create a lookup function for optionsets. Global optionssets are used unless "entity" is provided.
   */
  def makeOptionSetLookup(entity: Option[(String, String)] = None): LookupOption = {
    (value: String) => {
      Right[String, Int](10)
    }
  }

}
