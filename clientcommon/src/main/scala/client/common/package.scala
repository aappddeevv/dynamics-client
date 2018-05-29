// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package client
import io.estatico.newtype.macros.newtype

import scala.scalajs.js
import js.|
import dynamics.common.Utils.{merge}

/**
  * Common definitions for clients. Many of these common elements are OData
  * related since OData is the microsoft target web api protocol.
  */
package object common {
    /** Id newtype. */
  @newtype case class Id(asString: String)

  /** Entity set name newtype. */
  @newtype case class EntitySetName(asString: String)

  /** Entity logical name newtype. Could be entity or attribute. */
  @newtype case class EntityLogicalName(asString: String)

  import headers._

  val defaultMappers = Map[String, String => String](
    FormattedValue               -> (_ + "_fv"),
    AssociatedNavigationProperty -> (_ + "_anp"),
    LookupLogicalName            -> (_ + "_lln"),
  )

  val defaultODataToOmit = Seq[String](
    FormattedValue,
    AssociatedNavigationProperty,
    LookupLogicalName,
  )

  /**
    * Remap some CRM and OData response artifacts if found. Note that if O is a
    * non-native JS trait, you may want to ensure that you "add" attributes to
    * your trait that match the conventions specified so you can access the
    * attributes directly later. Mapping function is (attribute name) => (mapped
    * attribute name). key for mappers are the odata extensions. Object is
    * mutated directly. Clone prior if you want.  This is very expensive so its
    * *not* done by default in many methods.
    */
  def remapODataFields[O <: js.Object](obj: O, mappers: Map[String, String => String] = defaultMappers): O = {
    val d            = obj.asInstanceOf[js.Dictionary[js.Any]]
    val mergemeafter = js.Dictionary.empty[js.Any]
    for ((key, value) <- d) {
      // test each mapper! very expensive
      for ((mkey, f) <- mappers) {
        val odataKey = s"$key@$mkey"

        if (d.contains(odataKey))
          mergemeafter(f(key)) = d(odataKey)
      }
    }
    merge[O](obj, mergemeafter.asInstanceOf[O])
  }

  /** Remove any attribute that has a `@` annotation in its name. */
  def dropODataFields[O <: js.Object](obj: O, patterns: Seq[String] = defaultODataToOmit): O =
    dynamics.common.jsdatahelpers.omitIfMatch(obj.asInstanceOf[js.Dictionary[js.Any]], patterns).asInstanceOf[O]

}
