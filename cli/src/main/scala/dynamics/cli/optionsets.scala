// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package cli

import dynamics.common._

import scala.scalajs.js
import js._
import js.annotation._
import io.scalajs.nodejs._
import fs2._
import js.JSConverters._
import cats._
import cats.data._
import cats.implicits._
import js.Dynamic.{literal => jsobj}
import MonadlessIO._
import cats.effect._

import Utils._
import dynamics.common.implicits._
import dynamics.http._
import dynamics.http.implicits._
import dynamics.client.implicits._
import dynamics.client._
import client.common._

class OptionSetsActions(context: DynamicsContext) extends LazyLogger {

  import LocalizedHelpers._
  import OptionSetsActions._
  import context._

  protected def getList() = {
    val qs = QuerySpec(/*select = Seq("Name", "Description", "MetadataId")*/)
    dynclient.getList[OptionSetMetadata](qs.url(ENTITYSET))
  }

  protected def filter(r: Seq[OptionSetMetadata], filter: Seq[String]) =
    Utils.filterForMatches(r.map(a => (a, Seq(a.Name))), filter)

  protected def getFilteredList(data: Seq[OptionSetMetadata], criteria: Seq[String]) =
    filter(data, criteria)

  /** Finds global option set by name. If not found, returns None. Extremely expensive call
    * since it calls first to get the list with just the name, then a second call
    * to retrieve the entire object.
    */
  def findByName(name: String) = {
    val qs = QuerySpec(/*select = Seq("Name", "MetadataId")*/)
    dynclient.getList[OptionSetMetadata](qs.url(ENTITYSET)).map(_.filter(_.Name == name).headOption).flatMap {
      _ match {
        case Some(obj) =>
          dynclient.getOneWithKey[OptionSetMetadata](ENTITYSET, obj.MetadataId)
        case _ => IO.pure(None)
      }
    }
  }

  /** Get the global option set with just one call. */
  def findByName2(name: String): IO[OptionSetMetadata] = {
    dynclient.getOneWithKey[OptionSetMetadata](ENTITYSET, AltId("Name", name))
  }

  def list(): Action = Kleisli { config =>
    val topts  = new TableOptions(border = Table.getBorderCharacters(config.common.tableFormat))
    val header = Seq("#", "Name", "Description")
    lift {
      val filtered = unlift(getList().map(filter(_, config.common.filter)))
      val data =
        filtered.zipWithIndex.map {
          case (i, idx) =>
            println(s"${PrettyJson.render(i)}")
            Seq(idx.toString,
                i.Name,
                i.Description.label
            )
        }
      println(tablehelpers.render(header, data, topts))
    }
  }

}

object OptionSetsActions {
  val ENTITYSET = "GlobalOptionSetDefinitions"
}
