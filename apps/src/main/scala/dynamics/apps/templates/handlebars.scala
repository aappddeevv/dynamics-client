// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package apps
package facades
package handlebars

import scala.scalajs.js
import js.|
import js.annotation._
import js._

trait HandlebarsOptions extends js.Object {
  var data: js.UndefOr[Boolean]                   = js.undefined
  var compat: js.UndefOr[Boolean]                 = js.undefined
  var knownHelpersOnly: js.UndefOr[Boolean]       = js.undefined
  var noEscape: js.UndefOr[Boolean]               = js.undefined
  var strict: js.UndefOr[Boolean]                 = js.undefined
  var assumeObjects: js.UndefOr[Boolean]          = js.undefined
  var preventIndent: js.UndefOr[Boolean]          = js.undefined
  var ignoreStandalone: js.UndefOr[Boolean]       = js.undefined
  var explicitPartialContext: js.UndefOr[Boolean] = js.undefined
}

@js.native
@JSImport("handlebars", JSImport.Namespace)
object Handlebars extends js.Object {
  def compile(source: String): Template = js.native

  //def precompile(template: String, options: js.Object|js.Dynamic): TemplateSpec = js.native

  /** this context, item, options */
  def registerHelper(name: String, cb: js.ThisFunction2[js.Dynamic, js.Any, HelperOptions, String]): Unit = js.native
  def unregisterHelper(name: String): Unit                                                                = js.native

  val Utils: HandlebarsUtils = js.native

  def log(level: Int, message: String): Unit = js.native
}

@js.native
trait HandlebarsUtils extends js.Object {
  def isEmpty(value: js.Any): Boolean                                                = js.native
  def axtend(base: js.Object | js.Dynamic, value: js.Object | js.Dynamic): js.Object = js.native
  def toString(value: js.Any): String                                                = js.native
  def isArray(value: js.Any): Boolean                                                = js.native
  def isFunction(value: js.Any): Boolean                                             = js.native
  def escapeExpression(text: String): String                                         = js.native
}

@js.native
trait TemplateSpec extends js.Object {}

@js.native
trait Template extends js.Object {
  def apply(context: js.Object | js.Dynamic): String = js.native
}

@js.native
trait HelperOptions extends js.Object {
  def fn(value: js.Any): String = js.native
}
