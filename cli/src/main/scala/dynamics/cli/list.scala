// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package cli

import scala.scalajs.js
import js._
import js.{Array => arr}
import JSConverters._
import Dynamic.{literal => jsobj}
import cats._
import cats.data._
import cats.implicits._
import io.scalajs.npm.chalk._
import cats.effect._

import dynamics.common._
import dynamics.common.implicits._
import client.common._

object Listings {

  /**
    * Create an in-memory table for output. Potentially contains terminal escape
    * codes. You do not want to use this for large tables of more than a few
    * thousand rows.
    */
  def mkList[T](config: CommonConfig, items: Seq[T], colnames: Seq[String], cols: Option[js.Dynamic] = None)(
      flatten: T => Seq[String]): IO[String] = {
    IO {
      val topts = new TableOptions(border = Table.getBorderCharacters(config.tableFormat),
                                   columns = cols.getOrElse[js.Dynamic](jsobj()))
      val data: Seq[Seq[String]] =
        Seq(Seq("#") ++ colnames) ++
          items.zipWithIndex.map {
            case (i, idx) =>
              Seq((idx + 1).toString) ++ flatten(i)
          }
      Table.table(data.map(_.toJSArray).toJSArray, topts)
    }
  }
}
