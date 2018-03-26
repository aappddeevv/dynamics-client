// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package cli

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
import cats.effect._

import dynamics.common._
import MonadlessIO._
import Utils._
import dynamics.common.implicits._
import dynamics.http._
import dynamics.http.implicits._
import dynamics.client.QuerySpec
import dynamics.client.implicits._

@js.native
trait SDKMessageProcessingStep extends js.Object {
  val name: UndefOr[String]                       = js.undefined
  val description: UndefOr[String]                = js.undefined
  val sdkmessageprocessingstepid: UndefOr[String] = js.undefined
  val statecode: UndefOr[Int]                     = js.undefined
  val statuscode: UndefOr[Int]                    = js.undefined
  val mode: UndefOr[Int]                          = js.undefined
}

class SDKMessageProcessingStepsActions(context: DynamicsContext) extends LazyLogger {

  import SDKMessageProcessingStepsActions._
  import context._

  protected def getList() = {
    val qs = QuerySpec(select =
      Seq("name", "sdkmessageprocessingstepid", "solutionid", "statecode", "statuscode", "mode", "description"))
    dynclient.getList[SDKMessageProcessingStep](qs.url(ENTITYSET))
  }

  protected def filter(r: Seq[SDKMessageProcessingStep], filter: Seq[String]) =
    Utils.filterForMatches(r.map(a => (a, Seq(a.name.orEmpty, a.description.orEmpty))), filter)

  protected def getFilteredList(data: Seq[SDKMessageProcessingStep], criteria: Seq[String]) =
    filter(data, criteria)

  def list(): Action = Kleisli { config =>
    val topts  = new TableOptions(border = Table.getBorderCharacters(config.common.tableFormat))
    val header = Seq("#", "sdkmessageprocessingstepid", "name", "mode", "statecode", "statuscode")
    lift {
      val filtered = unlift(getList().map(filter(_, config.common.filter)))
      val data =
        filtered.zipWithIndex.map {
          case (i, idx) =>
            Seq(
              idx.toString,
              i.sdkmessageprocessingstepid.orEmpty,
              i.name.orEmpty,
              i.mode.map(_.toString).orEmpty,
              i.statecode.map(_.toString).orEmpty,
              i.statuscode.map(_.toString).orEmpty
            )
        }
      println(tablehelpers.render(header, data, topts))
    }
  }

  val doOne: (String, String, String) => IO[Unit] = (id, body, msg) =>
    dynclient.update(ENTITYSET, id, body).flatMap { str =>
      dynclient.getOneWithKey[SDKMessageProcessingStep](ENTITYSET, id).map { step =>
        println(s"$msg SDK message [${step.name}] with id ${id}")
      }
  }

  def mkBody(activate: Boolean) =
    if (activate) """{"statecode": 0, "statuscode": 1 }"""
    else """{"statecode": 1, "statuscode": 2 }"""

  def changeActivation(activate: Boolean, message: String) = Action { config =>
    val body = mkBody(activate)
    val doit = lift {
      val ids =
        if (config.common.filter.size > 0) {
          val list     = unlift(getList())
          val filtered = filter(list, config.common.filter)
          filtered.map(_.sdkmessageprocessingstepid.orEmpty).filterNot(_.isEmpty)
        } else
          Seq(config.sdkMessage.id)
      unlift(ids.toList.map(id => doOne(id, body, message)).sequence)
    }
    doit.map(_ => ())
  }

  def activate() = changeActivation(true, "Activated")

  def deactivate() = changeActivation(false, "Deactivated")
}

object SDKMessageProcessingStepsActions {
  val ENTITYSET = "sdkmessageprocessingsteps"
}
