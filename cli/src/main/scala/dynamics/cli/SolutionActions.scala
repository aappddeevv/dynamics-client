// Copyright (c) 2017 aappddeevv@gmail.com
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
import fs2.interop.cats._
import js.Dynamic.{literal => jsobj}

import dynamics.common._
import MonadlessTask._
import Utils._
import dynamics.common.implicits._
import dynamics.http._
import EntityDecoder._
import EntityEncoder._
import dynamics.client.implicits._
import dynamics.client._
import dynamics.http.syntax.all._

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
  implicit val solnDecoder = EntityDecoder.JsObjectDecoder[SolutionOData]

  protected def getList() = {
    val query =
      "/solutions?$select=description,friendlyname,ismanaged,_organizationid_value," +
        "_parentsolutionid_value,_publisherid_value,solutionid,solutionpackageversion," +
        "solutiontype,uniquename,version,versionnumber"
    dynclient.getList(query)
  }

  protected def filterSolutions(r: Seq[SolutionOData], filter: Seq[String]) = {
    r
  }

  /** Combinator to obtain web resources automatically */
  protected def withData(): Kleisli[Task, AppConfig, (AppConfig, Seq[SolutionOData])] =
    Kleisli { config =>
      {
        getList().map { wr =>
          val matchedItems = filterSolutions(wr, config.filter)
          (config, matchedItems)
        }
      }
    }

  protected def _list(): Kleisli[Task, (AppConfig, Seq[SolutionOData]), Unit] =
    Kleisli {
      case (config, wr) =>
        val topts  = new TableOptions(border = Table.getBorderCharacters(config.tableFormat))
        val header = Seq("#", "solutionid", "name", "displayname", "version")
        Task.delay {
          val data =
            wr.zipWithIndex.map {
              case (i, idx) =>
                Seq(idx.toString, i.solutionid.orEmpty, i.uniquename.orEmpty, i.friendlyname.orEmpty, i.version.orEmpty)
            }
          println(tablehelpers.render(header, data, topts))
        }
    }

  def list(): Action = withData andThen _list

  def upload(): Action = Kleisli { config =>
    val jsonconfig = config.solutionJsonConfigFile
      .map(Utils.slurp(_))
      .map(JSON.parse(_))
      .map(_.asInstanceOf[ImportSolution])
      .fold(new ImportSolution())(identity)
    println(s"Solution upload config:\n${PrettyJson.render(jsonconfig)}")

    if (!jsonconfig.ImportJobId.isDefined) jsonconfig.ImportJobId = CRMGUID()
    jsonconfig.CustomizationFile = slurp(config.solutionUploadFile, "base64")
    jsonconfig.PublishWorkflows = config.solutionPublishWorkflows

    val merged = mergeJSObjects(new ImportSolution(), jsonconfig)
    val x      = new ImportDataActions(context)

    println(s"Uploading solution file: ${config.solutionUploadFile}.")
    println(s"""Import job id: ${merged.ImportJobId.getOrElse("NA")}""")

    executeAction[String](ImportSolutionAction, merged.toEntity._1).flatMap { _ =>
      x.waitForJobStreamPrint(jsonconfig.ImportJobId.get)
    }
  }

  def export(): Action = Kleisli { config =>
    val jsonconfig = config.solutionJsonConfigFile
      .map(Utils.slurp(_))
      .map(JSON.parse(_))
      .map(_.asInstanceOf[ExportSolution])
      .fold(new ExportSolution())(identity)
    logger.debug(s"configfile content: ${Utils.render(jsonconfig)}")
    if (!jsonconfig.SolutionName.isDefined) jsonconfig.SolutionName = config.solutionName
    if (!jsonconfig.Managed.isDefined) jsonconfig.Managed = config.solutionExportManaged
    val merged = mergeJSObjects(new ExportSolution(), jsonconfig)

    val dec = EntityDecoder.ValueWrapper[SolutionOData]

    Task.delay(println(s"Export solution named ${config.solutionName}.")).flatMap { _ =>
      executeAction[ExportSolutionResponse](ExportSolutionAction, merged.toEntity._1).flatMap { filecontent =>
        val q = QuerySpec(filter = Some(s"uniquename eq '${merged.SolutionName}'"))
        getOne[SolutionOData](q.url("solutions"))(dec).flatMap { solninfo =>
          val mergedext = if (merged.Managed.getOrElse(false)) "_managed" else ""
          val version   = solninfo.version.map(_.replaceAllLiterally(".", "_")).getOrElse("noversion")
          val file      = pathjoin(config.outputDir, s"${merged.SolutionName}_${version}${mergedext}.zip")
          println(s"Output solution file: $file")
          writeToFile(file, fromBase64(filecontent.ExportSolutionFile.orEmpty))
        }
      }
    }
  }

  def delete() = Action { config =>
    val dec = EntityDecoder.ValueWrapper[SolutionJS] // expect a single record
    Task
      .delay(println(s"Deleting solution with name ${config.solutionName}."))
      .flatMap { _ =>
        val q = QuerySpec(filter = Some(s"uniquename eq '${config.solutionName}'"))
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

  def get(command: String): Action =
    command match {
      case "export" => export()
      case "list"   => list()
      case "delete" => delete()
      case "upload" => upload()
      case _ =>
        Action { _ =>
          Task.delay(println(s"solutions command '${command}' not recognized"))
        }
    }
}

object SolutionActions {
  val ImportSolutionAction = "ImportSolution"
  val ExportSolutionAction = "ExportSolution"

  val defaultAttributes = Seq("solutionid", "friendlyname", "uniquename", "solutiontype", "version", "ismanaged")

}
