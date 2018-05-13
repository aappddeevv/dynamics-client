// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package apps
package facades

import scala.scalajs.js

package object handlebars {

  /**
    * The template is not cached so don't do this in a tight loop.
    */
  def compileAndApplyTemplate(template: String, context: js.Object): String =
    applyTemplate(Handlebars.compile(template), context)

  /** Use a pre-compiled template. Adds some useful objects to the merged context. */
  def applyTemplate(template: Template, context: js.Object): String = {
    // anything else to do?
    template(context)
  }

}
