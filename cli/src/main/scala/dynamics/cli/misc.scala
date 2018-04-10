// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package cli

import scala.scalajs.js
import js._
import annotation._


@js.native
@JSImport("shorthash", JSImport.Namespace)
object ShortHash extends js.Object {
  def unique(in: String): String = js.native
}

/** Cache the metadata string returned from CRM. CSDL is cached across runs. */
// case class CSDLFileCache(name: String, context: DynamicsContext, ignore: Boolean = false, location: String = ".")
//     extends FileCache(Utils.pathjoin(location, ShortHash.unique(name) + ".csdl.cache"), ignore) {

//   protected def getContent() = (new MetadataActions(context)).getCSDL()

// }
