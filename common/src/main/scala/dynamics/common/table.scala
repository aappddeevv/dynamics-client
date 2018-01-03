// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package common

import scalajs.js
import js._
import js.annotation._

@js.native
@JSImport("table", JSImport.Namespace)
object Table extends js.Object {
  def table(data: js.Array[_ <: js.Array[_ <: scala.Any]], options: TableOptions): String = js.native
  def getBorderCharacters(templateName: String): js.Dictionary[String]                    = js.native
}

class TableOptions(
    val columns: js.UndefOr[js.Dynamic] = js.undefined,
    val columnDefault: js.UndefOr[js.Dynamic] = js.undefined,
    val border: js.UndefOr[String | js.Dictionary[String]] = js.undefined,
    val drawJoin: js.UndefOr[js.Function2[Int, Int, Unit]] = js.undefined
    // ...
) extends js.Object

object TableOptions {
  val Empty = new TableOptions()
}

/*
@ScalaJSDefined
class Column(
  val alignment: js.UndefOr[String] = "left",
  val width: js.UndefOr[Int] = js.undefined,
  val truncate: js.UndefOr[Int] = js.undefined,
  val paddingLeft: js.UndefOr[Int] = 0,
  val paddingRight: js.UndefOr[Int] = 0,
  val wrapWord: js.UndefOr[Boolean] = true
) extends js.Object
 */
