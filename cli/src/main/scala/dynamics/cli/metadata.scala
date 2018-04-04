// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package cli

import scala.scalajs.js
import js._
import annotation._
import JSConverters._
import js.Dynamic.{literal => jsobj}
import scala.concurrent._
import scala.concurrent.duration._
import fs2._
import cats._
import cats.data._
import cats.implicits._
import cats.effect._
import io.scalajs.npm.chalk._

import dynamics.common._
import MonadlessIO._
import dynamics.http._
import dynamics.client._
import dynamics.client.implicits._
import dynamics.common.implicits._
import dynamics.http.implicits._
import client.common._

class MetadataActions(val context: DynamicsContext) {

  import dynamics.client.syntax.queryspec._
  import context._
  val m = new MetadataCache(context)

  //val mc = new MetadataCache(context)

  def getCSDL(): IO[String] = {
    val request =
      HttpRequest[IO](Method.GET, "/$metadata", headers = HttpHeaders("Accept" -> "application/xml;charset=utf8"))
    dynclient.http.expect[String](request)
  }

  def listEntities() = Action { config =>
    println("Entities")
    val q     = QuerySpec(select = Seq("LogicalName", "EntitySetName", "PrimaryIdAttribute", "ObjectTypeCode"))
    val topts = new TableOptions(border = Table.getBorderCharacters(config.common.tableFormat))
    lift {
      val list =
        unlift(m.entityDefinitions()).sortBy(_.LogicalName)
      val data: Seq[Seq[String]] =
        Seq(Seq("#", "LogicalName", "EntitySetName", "PrimaryIdAttribute", "ObjectTypeCode")) ++
          list.zipWithIndex.map {
            case (i, idx) =>
              Seq((idx + 1).toString,
                  i.LogicalName,
                  i.EntitySetName,
                  i.PrimaryIdAttribute,
                  i.ObjectTypeCode.toString)
          }
      val out = Table.table(data.map(_.toJSArray).toJSArray, topts)
      println(out)
    }
  }

  def downloadCSDL() = Action { config =>
    config.common.outputFile
      .fold(IO(println("An output file name is required for the downloaded CSDL.")))(ofile => {
        println(s"Downloading CSDL to output file ${config.common.outputFile}")
        getCSDL().flatMap(Utils.writeToFile(ofile, _))
      })
  }

  def test() = Action { config =>
    println("Running metadata test.")
    lift {
      val csdl = unlift(dynclient.http.expect[String](MetadataActions.DownloadCSDLRequest))
      /*
      val mc = new MetadataCache(context, csdl)
      val t = mc.typeOf("contact", "spruce_satmath")
      println(s"t = $t")
       */
      ()
    }

  }

}

object MetadataActions {

  /** An HttpRequest that can be used with Client to obtain the CSDL. */
  val DownloadCSDLRequest =
    HttpRequest[IO](Method.GET, "/$metadata", headers = HttpHeaders("Accept" -> "application/xml;charset=utf8"))
}
