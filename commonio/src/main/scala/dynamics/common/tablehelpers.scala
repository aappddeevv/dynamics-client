// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package common

import scala.scalajs.js
import js.JSConverters._
import io.scalajs.npm.chalk._

/** Helpers for printing tables. */
object tablehelpers {

  /** Render a strict table. Non-streaming. */
  def render(header: Seq[String], data: Seq[Seq[String]], opts: TableOptions): String = {
    val alldata = Seq(bold(header)) ++ data
    Table.table(alldata.map(_.toJSArray).toJSArray, opts)
  }

  /** Bold up the row, as in header row. */
  def bold(data: Seq[String]): Seq[String] = data.map(Chalk.bold(_))

}
