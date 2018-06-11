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

import common._
import MonadlessIO._
import common.Utils._
import common.implicits._
import dynamics.http._ // nodejs has an http pkg
import dynamics.http.implicits._
import client._
import client.implicits._
import client.common._

class ExportSolution(
    /** Required */
    var SolutionName: UndefOr[String] = js.undefined,
    /** Required */
    var Managed: UndefOr[Boolean] = js.undefined,
    var TargetVersion: UndefOr[String] = js.undefined,
    var ExportAutoNumberingSettings: UndefOr[Boolean] = js.undefined,
    var ExportCalendarSettings: UndefOr[Boolean] = js.undefined,
    var ExportCustomizationSettings: UndefOr[Boolean] = js.undefined,
    var ExportEmailTrackingSettings: UndefOr[Boolean] = js.undefined,
    var ExportGeneralSettings: UndefOr[Boolean] = js.undefined,
    var ExportMarketingSettings: UndefOr[Boolean] = js.undefined,
    var ExportOutlookSynchronizationSettings: UndefOr[Boolean] = js.undefined,
    var ExportRelationshipRoles: UndefOr[Boolean] = js.undefined,
    var ExportIsvConfig: UndefOr[Boolean] = js.undefined,
    var ExportSales: UndefOr[Boolean] = js.undefined,
    var ExportExternalApplications: UndefOr[Boolean] = js.undefined
) extends js.Object

@js.native
trait SolutionJS extends js.Object {
  var solutionid: String   = js.native
  var friendlyname: String = js.native
  var uniquename: String   = js.native
  var solutiontype: Int    = js.native
  var version: String      = js.native
  var ismanaged: Boolean   = js.native
}

@js.native
trait ExportSolutionResponse extends js.Object {

  /** Compressed zip, base64. */
  val ExportSolutionFile: UndefOr[String] = js.native
}

class SolutionActions(context: DynamicsContext) extends LazyLogger {

  import SolutionActions._
  import context._
  import dynclient._
  implicit val solnDecoder = JsObjectDecoder[SolutionOData]()

  protected def getList() = {
    val qs = QuerySpec(select = Seq(
      "description",
      "friendlyname",
      "ismanaged",
      "_organizationid_value",
      "_parentsolutionid_value",
      "_publisherid_value",
      "solutionid",
      "solutionpackageversion",
      "solutiontype",
      "uniquename",
      "version",
      "versionnumber"))
    dynclient.getList(qs.url("solutions"))
  }

  protected def filterSolutions(r: Seq[SolutionOData], filter: Seq[String]) = {
    r
  }

  /** Combinator to obtain web resources automatically */
  protected def withData(): Kleisli[IO, AppConfig, (AppConfig, Seq[SolutionOData])] =
    Kleisli { config =>
      {
        getList().map { wr =>
          val matchedItems = filterSolutions(wr, config.common.filter)
          (config, matchedItems)
        }
      }
    }

  protected def _list(): Kleisli[IO, (AppConfig, Seq[SolutionOData]), Unit] =
    Kleisli {
      case (config, items) =>
        Listings.mkList(config.common, items,
          Seq("solutionid", "uniquename", "displayname", "version")){ i =>
          Seq(i.solutionid.orEmpty, i.uniquename.orEmpty, i.friendlyname.orEmpty, i.version.orEmpty)
        }
          .map(println)
    }

  def list(): Action = withData andThen _list

  def upload(): Action = Kleisli { config =>
    val jsonconfig = config.solution.solutionJsonConfigFile
      .map(IOUtils.slurp(_))
      .map(JSON.parse(_))
      .map(_.asInstanceOf[ImportSolution])
      .fold(new ImportSolution())(identity)
    println(s"Solution upload config:\n${PrettyJson.render(jsonconfig)}")

    if (!jsonconfig.ImportJobId.isDefined) jsonconfig.ImportJobId = CRMGUID()
    jsonconfig.CustomizationFile = IOUtils.slurp(config.solution.solutionUploadFile, "base64")
    jsonconfig.PublishWorkflows = config.solution.solutionPublishWorkflows

    val merged = merge(new ImportSolution(), jsonconfig)
    val x      = new ImportDataActions(context)

    println(s"Uploading solution file: ${config.solution.solutionUploadFile}.")
    println(s"""Import job id: ${merged.ImportJobId.getOrElse("NA")}""")

    executeAction[String](ImportSolutionAction, merged.toEntity._1).flatMap { _ =>
      x.waitForJobStreamPrint(jsonconfig.ImportJobId.get)
    }
  }

  def export() = Action { config =>
    val jsonconfig = config.solution.solutionJsonConfigFile
      .map(IOUtils.slurp(_))
      .map(JSON.parse(_))
      .map(_.asInstanceOf[ExportSolution])
      .fold(new ExportSolution())(identity)
    logger.debug(s"configfile content: ${IOUtils.render(jsonconfig)}")
    if (!jsonconfig.SolutionName.isDefined) jsonconfig.SolutionName = config.solution.solutionName
    if (!jsonconfig.Managed.isDefined) jsonconfig.Managed = config.solution.solutionExportManaged
    val merged = merge(new ExportSolution(), jsonconfig)

    val dec = ValueWrapper[SolutionOData]

    IO(println(s"Export solution named ${config.solution.solutionName}.")).flatMap { _ =>
      executeAction[ExportSolutionResponse](ExportSolutionAction, merged.toEntity._1).flatMap { filecontent =>
        val q = QuerySpec(filter = Some(s"uniquename eq '${merged.SolutionName}'"))
        getOne[SolutionOData](q.url("solutions"))(dec).flatMap { solninfo =>
          val mergedext = if (merged.Managed.getOrElse(false)) "_managed" else ""
          val version   = solninfo.version.map(_.replaceAllLiterally(".", "_")).getOrElse("noversion")
          val file      = IOUtils.pathjoin(config.common.outputDir, s"${merged.SolutionName}_${version}${mergedext}.zip")
          println(s"Output solution file: $file")
          IOUtils.writeToFile(file, IOUtils.fromBase64(filecontent.ExportSolutionFile.orEmpty))
        }
      }
    }
  }

  def delete() = Action { config =>
    val dec = ValueWrapper[SolutionJS] // expect a single record
    IO(println(s"Deleting solution with name ${config.solution.solutionName}."))
      .flatMap { _ =>
        val q = QuerySpec(filter = Some(s"uniquename eq '${config.solution.solutionName}'"))
        getOne(q.url("solutions"))(dec)
      }
      .flatMap(s => dynclient.delete("solutions", s.solutionid))
      .map(result => {
        if (result._2) println("Solution deleted.")
        else {
          println("Failed to delete solution")
        }
      })
  }

  val publishAll = Action { config =>
    dynclient.executeAction("PublishAllXml", Entity.empty, None)(void)
  }

  def get(command: String): Action =
    command match {
      case "export"     => export()
      case "list"       => list()
      case "delete"     => delete()
      case "upload"     => upload()
      case "publishAll" => publishAll
      case _ =>
        Action { _ =>
          IO(println(s"solutions command '${command}' not recognized."))
        }
    }
}

object SolutionActions {
  val ImportSolutionAction = "ImportSolution"
  val ExportSolutionAction = "ExportSolution"

  val defaultAttributes = Seq("solutionid", "friendlyname", "uniquename", "solutiontype", "version", "ismanaged")

}
