// Copyright (c) 2017 aappddeevv@gmail.com
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
import fs2._
import cats._
import cats.data._
import cats.implicits._
import io.scalajs.npm.chalk._
import cats.effect._

import dynamics.common._
import dynamics.client
import client._
import dynamics.common.syntax.jsdynamic._
import dynamics.http
import http._
import client.implicits._
import http.implicits._
import NPMTypes._

object ImportMapUtils {

  /** Get SourceEntityname and TargetEntityName from the first EntityMap.
    * Throw exceptions if anything is not found.
    * @param xml XML string to be parsed.
    * @param source Source identifier for error messages.
    */
  def getSourceAndTarget(xml: String, source: String): (String, String) = {
    val mapXml = new XmlDocument(xml)
    val entry: js.UndefOr[js.Array[XmlElement]] =
      mapXml.childNamed("EntityMaps").childrenNamed("EntityMap") // array of
    entry
      .map(_(0).asInstanceOf[js.Dynamic])
      .map { entry =>
        //println(s"EntityMap entry: " + Utils.render(entry))
        (entry.attr.SourceEntityName.asString, entry.attr.TargetEntityName.asString)
      }
      .getOrElse(throw new RuntimeException(
        s"Import map xml [$source] is malformed. Unable to find first EntityMap entry.\n$xml"))
  }

}

class ImportMapActions(context: DynamicsContext) extends LazyLogger {

  import ImportMapActions._
  import context._

  implicit val dec1 = JsObjectDecoder[ExportMappingsImportMapResponse]
  implicit val dec2 = JsObjectDecoder[ImportMapOData]
  implicit val enc  = JsObjectEncoder[ImportMappingsImportMap]

  def getList() = {
    val query = "/importmaps"
    dynclient.getList[ImportMapOData](query)
  }

  def filter(items: Traversable[ImportMapOData], filters: Seq[String]) =
    Utils.filterForMatches(items.map(a => (a, Seq(a.name, a.description, a.importmapid))), filters)

  /** Get an import map's XML content. */
  def getImportMapXml(importMapId: String): IO[String] = {
    import context._
    dynclient
      .executeAction[ExportMappingsImportMapResponse](ExportMappingsImportMapAction,
                                                      Entity.fromString("{ ExportIds: false }"),
                                                      Some(("importmaps", importMapId)))
      .map(_.MappingsXml)
  }

  def download() = Action { config =>
      getList()
        .map(filter(_, config.common.filter))
        .map { _.map(imap => (imap.importmapid, imap.name)) }
        .flatMap { ids =>
          ids.toList.traverse {
            case (id, name) =>
              dynclient
                .executeAction[js.Dynamic]("Microsoft.Dynamics.CRM.ExportMappingsImportMap",
                  Entity.fromString("{ ExportIds: false }"),
                  Some(("importmaps", id)))(JSONDecoder)
                .flatMap { jsdyn =>
                  val resp = jsdyn.asInstanceOf[ExportMappingsImportMapResponse]
                  val path = Utils.pathjoin(config.common.outputDir, s"${name}.xml")
                  val doit: IO[Unit] =
                    if (config.common.noclobber && Utils.fexists(path))
                      IO(println(s"Importmap download file $path exists and noclobber is set."))
                    else
                      Utils
                        .writeToFile(path, resp.MappingsXml)
                        .map(_ => println(s"Wrote importmap file: $path"))
                  doit
                }
          }.map(_ => ()) // just to make it return the right value, Unit
        }
  }

  def list(): Action = Kleisli { config =>
    {
      val cols = jsobj("3" -> jsobj(width = 60))
      getList().map(filter(_, config.common.filter)).map { items =>
        val topts = new TableOptions(border = Table.getBorderCharacters(config.common.tableFormat), columns = cols)
        val data =
          Seq(Seq("#", "importmapid", "name", "targetentity", "description").map(Chalk.bold(_))) ++
            items.zipWithIndex.map {
              case (i, idx) =>
                Seq((idx + 1).toString, i.importmapid, i.name, i.targetentity, i.description)
            }
        val out = Table.table(data.map(_.toJSArray).toJSArray, topts)
        println("Import maps")
        println(out)
      }
    }
  }

  /** Import a map. Overwrites anything that already exists with the same name. */
  def importImportMap(xmlContent: String): IO[ImportMapOData] = {
    val payload = new ImportMappingsImportMap(MappingsXml = xmlContent)
    dynclient.executeAction[ImportMapOData](ImportMappingsImportMapAction, payload.toEntity._1)
  }

  import io.scalajs.npm.xml2js._
  import dynamics.common.implicits._

  /** Upload a single import map. Potentially clobber it if it already exists. */
  def uploadOne(file: String, noclobber: Boolean): IO[Unit] = {
    // test to see if it already exists, if so, delete it?
    val contents: String             = Utils.slurp(file)
    val f: JSCallbackNPM[js.Dynamic] = Xml2js.parseString[js.Dynamic](contents, _)

    val checkAndMaybeDelete: IO[(Boolean, String)] =
      f.toIO.flatMap { mapdata =>
        //println("JSON: " + Utils.render(mapdata.asJSObject))
        val mapname = mapdata.Map.`$`.Name.asString
        val qs      = QuerySpec(filter = Some(s"name eq '$mapname'"))
        dynclient.getList[ImportMapOData](qs.url("importmaps")).flatMap { mlist =>
          if (mlist.size > 0) {
            // map exists, maybe we should clobber?
            println(s"Map $mapname already exists on server.")
            if (!noclobber) {
              // delete it first
              println("Command option noclobber is false so the import map on the server will be deleted first.")
              dynclient.delete("importmaps", mlist(0).importmapid).map(_ => (true, mapname))
            } else {
              // do not clobber!
              println("Command option noclobber is true so the import map on the server will not be changed.")
              IO.pure((false, mapname))
            }
          } else {
            // nothing there!
            IO.pure((true, mapname))
          }
        }
      }

    checkAndMaybeDelete.flatMap(_ match {
      case (true, name) =>
        importImportMap(Utils.slurp(file)).attempt.map {
          case Right(_) => println(s"Import map uploaded from file ${file} to map $name successfully.")
          case Left(e) =>
            println(s"Upload failed. Was there a connectivity problem?")
            logger.error(e.toString)
        }
      case (false, file) =>
        IO(println(s"Import map $file was not uploaded."))
    })
  }

  def upload(files: Seq[String], noclobber: Boolean = true): IO[Unit] = {
    val loads = files.map { f =>
      if (Utils.fexists(f)) uploadOne(f, noclobber)
      else IO(println(s"Import map $f is not accessible to upload."))
    }
    loads.toList.sequence.map(_ => ())
  }

  /** Upload and optionally clobber an import map. */
  def upload(): Action = Kleisli { config =>
    upload(config.importdata.importMapUploadFilename, config.common.noclobber)
  }

}

object ImportMapActions {
  val ExportMappingsImportMapAction = "Microsoft.Dynamics.CRM.ExportMappingsImportMap"
  val ImportMappingsImportMapAction = "ImportMappingsImportMap"
}
