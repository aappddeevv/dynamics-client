// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package cli

import scala.scalajs.js
import js._
import JSConverters._
import io.scalajs.nodejs._
import scala.concurrent._
import io.scalajs.util.PromiseHelper.Implicits._
import fs2._
import cats._
import cats.data._
import cats.implicits._
import io.scalajs.npm.chalk._
import js.Dynamic.{literal => jsobj}
import cats.effect._

import dynamics.common._
import dynamics.common.implicits._
import dynamics.client._
import dynamics.http._
import dynamics.http.implicits._

class PublisherActions(context: DynamicsContext) {

  import context._
  implicit val dec = JsObjectDecoder[PublisherOData]

  protected def getList() = {
    val query =
      "/publishers?$select=customizationprefix,description,friendlyname," + "_organizationid_value,publisherid,uniquename"
    dynclient.getList[PublisherOData](query)
  }

  def filter(r: Seq[PublisherOData], filter: Seq[String]) = {
    r
  }

  protected def withData(): Kleisli[IO, AppConfig, (AppConfig, Seq[PublisherOData])] =
    Kleisli { config =>
      getList()
        .map { wr =>
          filter(wr, config.common.filter)
        }
        .map((config, _))
    }

  protected def _list(): Kleisli[IO, (AppConfig, Seq[PublisherOData]), Unit] =
    Kleisli {
      case (config, wr) =>
        IO {
          println("Publishers")
          val cols  = jsobj("5" -> jsobj(width = 40))
          val topts = new TableOptions(border = Table.getBorderCharacters(config.common.tableFormat), columns = cols)
          val data: Seq[Seq[String]] =
            Seq(Seq("#", "publisherid", "uniquename", "friendlyname", "customizationprefix", "description")) ++
              wr.zipWithIndex.map {
                case (i, idx) =>
                  Seq((idx + 1).toString,
                      i.publisherid,
                      i.uniquename,
                      i.friendlyname,
                      i.customizationprefix,
                      i.description)
              }
          val out = Table.table(data.map(_.toJSArray).toJSArray, topts)
          println(out)
        }
    }

  def list() = withData andThen _list

}
