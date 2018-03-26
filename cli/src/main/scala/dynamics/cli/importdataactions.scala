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
import io.scalajs.npm.chalk._
import cats.effect._

import dynamics.common._
import MonadlessIO._
import dynamics.client._
import dynamics.http._
import dynamics.common.syntax.jsdynamic._
import dynamics.client.implicits._
import dynamics.http.implicits._

case class EnityReference(entity: String, id: String)

class ImportDataActions(val context: DynamicsContext) {

  import context._
  import dynamics.common.implicits._
  import ImportDataActions._

  implicit val jobDecoder            = JsObjectDecoder[AsyncOperationOData]
  implicit val importJsonDecoder     = JsObjectDecoder[ImportJson]
  implicit val importFileJsonDecoder = JsObjectDecoder[ImportFileJson]
  implicit val WhoAmIDecoder         = JsObjectDecoder[WhoAmI]
  implicit val dec10                 = JsObjectDecoder[BulkDeleteResponse]

  type WaitTuple   = (Int, String, AsyncOperationOData) // status, msg, data
  type WaitHandler = PartialFunction[Either[Throwable, AsyncOperationOData], Option[WaitTuple]]

  /** Looks for statecode = 3. If job not found, returns None. */
  def waitHandler(id: String): WaitHandler = {
    case Right(a) =>
      val statelabel  = AsyncOperation.StateCodes(a.statecode.get)
      val statuslabel = AsyncOperation.StatusCodes(a.statuscode.get)
      val msg         = s"${js.Date()}: Waiting for job to complete: state = $statelabel, status = ${statuslabel}."
      if (a.statecode.getOrElse(-1) == 3) None else Some((a.statuscode.get, msg, a))
    case Left(t: DynamicsError) =>
      if (t.status == Status.NotFound) {
        //println(s"${js.Date()}: Job with id $id not found.")
        None
      } else throw t
  }

  def waitForJobStream(jobid: String, delta: FiniteDuration = 10.seconds, handler: WaitHandler): Stream[IO, WaitTuple] =
    fs2helpers.unfoldEvalWithDelay[IO, WaitTuple]({
      dynclient.getOneWithKey[AsyncOperationOData]("asyncoperations", jobid).attempt.map { handler(_) }
    }, _ => delta)

  def waitForJobStreamPrint(jobid: String, delta: FiniteDuration = 10.seconds): IO[Unit] = {
    waitForJobStream(jobid, delta, waitHandler(jobid)).zipWithPrevious.map {
      _ match {
        case (None, curr)                               => println(curr._2)
        case (Some(prev), curr) if (prev._1 != curr._1) => println(curr._2)
        case _                                          => ()
      }
    }
  }.compile.drain

  def reportImportStatus(importid: String): IO[Unit] =
    dynclient.getOneWithKey[ImportJson]("imports", importid).map { i =>
      val msg = AsyncOperation.ImportStatusCode
        .get(i.statuscode.getOrElse(-1))
        .getOrElse(s"No status code label for value ${i.statuscode}")
      println(s"Import status (last process step completed): $msg")
    }

  def reportErrors(importfileid: String) =
    dynclient.getList[ImportLogJS](s"/importlogs?$$filter=importfileid/importfileid eq $importfileid").map { i =>
      println("TODO: Make this a table.")
      println(s"${Utils.render(i)}")
    }

  val ProcessingStatus = Map(
    1  -> "Not Started",
    2  -> "Parsing",
    3  -> "Parsing Complete",
    4  -> "Complex Transformation",
    5  -> "Lookup Transformation",
    6  -> "Picklist Transformation",
    7  -> "Owner Transformation",
    8  -> "Transformation Complete",
    9  -> "Import Pass 1",
    10 -> "Import Pass 2",
    11 -> "Import Complete",
    12 -> "Primary Key Transformation"
  )

  val importData: Action = Kleisli { config =>
    {

      val path          = config.importdata.importDataInputFile
      val importmapname = config.importdata.importDataImportMapName
      val name =
        config.importdata.importDataName.getOrElse(Utils.filename(path).get) + " using " + importmapname
      println(s"Import job name: [$name]")

      val filename = Utils.namepart(path)

      val ftype     = Utils.extension(path)
      val ftypeint  = ftype.map(ext => FileType.extToInt(ext.toLowerCase)).getOrElse(FileType.csv)
      val modecode  = if (config.importdata.importDataCreate) ModeCode.Create else ModeCode.Update
      val importRec = new ImportJson(s"$name", modecode = modecode)
      val waitforit = waitForJobStreamPrint(_: String, config.importdata.importDataPollingInterval.seconds) //curry

      // val x = lift {
      //   new TestJS(name = "blah")
      // }

      if (!Utils.fexists(path))
        IO(println(s"File $path is not accessible for importing."))
      else
        lift {
          println("Creating import.")
          val importid = unlift(dynclient.createReturnId("imports", JSON.stringify(importRec)))
          println(s"Processing import job [$name] with id $importid.")

          val whoami = unlift(dynclient.executeFunction[WhoAmI]("WhoAmI"))
          println(s"Using system user record owner with id ${whoami.UserId}")

          val qurl        = s"/importmaps?$$filter=name eq '$importmapname'&$$select=importmapid"
          val importmapid = unlift(dynclient.getList[ImportMapOData](qurl).map(_(0).importmapid))
          println(s"Using import map $importmapname with id $importmapid")

          val mappingXml = unlift(new ImportMapActions(context).getImportMapXml(importmapid))
          val (s, t)     = ImportMapUtils.getSourceAndTarget(mappingXml, importmapname)

          //val x = new TestJS(name = "blah")

          val content        = Utils.slurp(path)
          val recordsOwnerId = config.importdata.recordsOwnerId.getOrElse(whoami.UserId)
          val importfile = new ImportFileJson {
            name = s"$name import"
            source = filename.get
            filetypecode = ftypeint
            content = content
            isfirstrowheader = true
            usesystemmap = false
            enableduplicatedetection = config.importdata.importDataEnableDuplicateDetection
            sourceentityname = s // must be the same as in the mapping file
            targetentityname = t // must be the same as the mapping file
            fielddelimitercode = FieldDelimiter.comma
            datadelimitercode = DataDelimiter.doublequote
            processcode = ProcessCode.Process
            importid = s"/imports($importid)"
            importmapid = s"/importmaps($importmapid)"
            recordsownerid_systemuser = s"/systemusers($recordsOwnerId)"
          }
          println("Starting import file stage.")
          val ifileid = unlift(dynclient.createReturnId("importfiles", JSON.stringify(importfile)))
          println(s"Processing import file: $ifileid")

          val after = (jobid: String) =>
            for {
              _ <- waitforit(jobid)
              _ <- reportImportStatus(importid)
              _ <- reportErrors(ifileid)
            } yield jobid

          println("Starting parsing stage.")
          unlift(requestParsing(importid) flatMap after)

          println("Starting transform stage.")
          unlift(requestTransform(importid) flatMap after)

          println("Starting import records to CRM database stage.")
          unlift(requestImport(importid) flatMap after)

          // Report import stats from the input file...
          unlift(reportImportFileBasicStats(ifileid, config.common.debug))

          // Report final status.
          unlift(reportImportStatus(importid))
        }
    }
  }

  def reportImportFileBasicStats(ifileid: String, debug: Boolean = false): IO[Unit] = {
    dynclient.getOneWithKey[ImportFileJson]("importfiles", ifileid).map { importrec =>
      if (debug) println(s"Importfile record: ${PrettyJson.render(importrec)}")
      println(s"Status       : ${importrec.statuscode}")
      println(s"Total count  : ${importrec.totalcount}")
      println(s"Success count: ${importrec.successcount}")
      println(s"Failure count: ${importrec.failurecount}")
    }
  }

  /** Return the jobid of the parsing job. */
  def requestParsing(importid: String): IO[String] = {
    dynclient
      .executeAction[AsyncOperationOData]("Microsoft.Dynamics.CRM.ParseImport",
                                          Entity.fromString(""),
                                          Option(("imports", importid)))
      .map(_.asyncoperationid.get)
  }

  /** Return the jobid of the transform job. */
  def requestTransform(importid: String): IO[String] = {
    dynclient
      .executeAction[AsyncOperationOData]("TransformImport",
                                          Entity.fromString(s"""{ "ImportId": "$importid"  }"""),
                                          None)
      .map(_.asyncoperationid.get)
  }

  /** Return the jobid of the import job. */
  def requestImport(importid: String): IO[String] = {
    dynclient
      .executeAction[AsyncOperationOData]("Microsoft.Dynamics.CRM.ImportRecordsImport",
                                          Entity.fromString(""),
                                          Option(("imports", importid)))
      .map(_.asyncoperationid.get)
  }

  val listImportFiles: Action = Kleisli { config =>
    val opts = new TableOptions(border = Table.getBorderCharacters(config.common.tableFormat))
    val header = Seq("#",
                     "importfileid",
                     "name",
                     "processingstatus",
                     "failurecounut",
                     "partialfailurecount",
                     "totalcount",
                     "statuscode",
                     "createdon")

    dynclient.getList[ImportFileJson]("/importfiles?$orderby=createdon asc").map { list =>
      val data = list.zipWithIndex.map {
        case (i, idx) =>
          Seq(
            (idx + 1).toString,
            i.importfileid.orEmpty,
            i.name.orEmpty,
            i.processingstatus_fv.orEmpty,
            i.failurecount.map(_.toString).orEmpty,
            i.partialfailurecount.map(_.toString).orEmpty,
            i.totalcount.map(_.toString).orEmpty,
            i.statuscode_fv.orEmpty,
            i.createdon.orEmpty
          )
      }
      println(tablehelpers.render(header, data, opts))
    }
  }

  val resume: Action = Kleisli { config =>
    println("Resume import data processing...")

    lift {
      val theImport =
        unlift(dynclient.getOneWithKey[ImportJson]("/imports", config.importdata.importDataResumeImportId.id))
      val theImportfile =
        unlift(
          dynclient.getOneWithKey[ImportFileJson]("/importfiles", config.importdata.importDataResumeImportFileId.id))

      println("import")
      PrettyJson.render(theImport)
      println("importfile")
      PrettyJson.render(theImportfile)
    }
  }

  val listImports: Action = Kleisli { config =>
    val q = QuerySpec()
      .withExpand(
        Expand(
          "Import_ImportFile",
          select =
            Seq("name", "processingstatus", "successcount", "statuscode", "statecode", "totalcount", "failurecount")))
      .withExpand(Expand("Import_AsyncOperations"))
      .withOrderBy("createdon asc")

    val opts   = new TableOptions(border = Table.getBorderCharacters(config.common.tableFormat))
    val header = Seq("#", "importid", "name", "statuscode", "createdon")

    lift {
      val list = unlift(dynclient.getList[ImportJson](q.url("imports")))
      if (list.size == 0) println("No importfiles found.")
      else {
        val data = list.zipWithIndex.map {
          case (i, idx) =>
            val statuscode = AsyncOperation.ImportStatusCode
              .get(i.statuscode.getOrElse(-1))
              .getOrElse(s"No status code label for value ${i.statuscode}")
            Seq((idx + 1).toString, i.importid.getOrElse("NA"), i.name, statuscode, i.createdon.getOrElse("NA"))
        }
        val out = tablehelpers.render(header, data, opts)
        println(out)
      }
    }
  }

  val delete = Action { config =>
    val filterOne = Utils.filterOneForMatches[ImportJson](imp => Seq(imp.name), config.common.filter)
    val q = QuerySpec(
      select = Seq("importid", "name")
    )
    val deleteone = (id: String) =>
      dynclient.delete("imports", id).flatMap { id =>
        IO(println(s"[$id] Deleted on ${new Date().toISOString()}."))
    }
    val counter = new java.util.concurrent.atomic.AtomicInteger(0)

    dynclient
      .getListStream[ImportJson](q.url("imports"))
      .filter(filterOne)
      .evalMap { imp =>
        deleteone(imp.importid.get)
      }
      .map { imp =>
        counter.getAndIncrement(); imp
      }
      .compile
      .drain
      .map(_ => println(s"${counter.get} imports delete."))
  }

  val bulkDelete: Action = Kleisli { config =>
    println("Bulk delete - yeah, this is under importdata :-)")

    // convert queryjson to js.Array[js.Object]
    val qjson = config.importdata.importDataDeleteQueryJson.map(JSON.parse(_).asInstanceOf[js.Array[js.Object]])

    val req = new BulkDeleteAction(
      QuerySet = qjson.getOrElse(js.Array()),
      JobName = config.importdata.importDataDeleteJobName,
      SourceImportId = config.importdata.importDataDeleteImportId,
      StartDateTime =
        config.importdata.importDataDeleteStartTime.getOrElse(Utils.addMinutes(new js.Date(), 3).toISOString()),
      RecurrencePattern = config.importdata.importDataDeleteRecurrencePattern.getOrElse("")
    )
    dynclient.executeAction[BulkDeleteResponse](BulkDeleteAction, req.toEntity._1).flatMap { resp =>
      resp.JobId.fold({
        IO(println(s"No job id was returned to monitor."))
      })({ id =>
        println(s"Bulk delete job [${req.JobName}] id: ${resp.JobId}")
        waitForJobStreamPrint(id)
      })
    }
  }

  val dumpErrors = Action { config =>
    val outdir = config.common.outputDir
    val qs = QuerySpec(
      filter = Some("(failurecount gt 0) or (partialfailurecount gt 0)"),
      expand = Seq(Expand("ImportFile_ImportData"))
    )
    lift {
      val list = unlift(dynclient.getList[ImportFileJson](qs.url("importfiles")))
      list.foreach(i => js.Dynamic.global.console.log("importfile", i))
      //val edata = unlift(list.map(ifile => dynclient.getList[ImportDataJS](ifile.ImportFile_ImportData_nl.get)).toList.sequence)
      // if there is alot of errors, perhasp this is non-performant?
      val edata = unlift(
        list
          .map { ifile =>
            val qsdata = QuerySpec(filter = Some(s"_importfileid_value eq ${ifile.importfileid.get}"))
            dynclient.getList[ImportLogJS](qsdata.url("importlogs"))
          }
          .toList
          .sequence)
      IO(println("blah"))
        .map(_ => edata.foreach(id => js.Dynamic.global.console.log("importdata", id)))
    }
  }

  def get(command: String): Action =
    command match {
      case "import"          => importData
      case "listimports"     => listImports
      case "listimportfiles" => listImportFiles
      case "bulkdelete"      => bulkDelete
      case "resume"          => resume
      case "delete"          => delete
      case "dumperrors"      => dumpErrors
      case _ =>
        Action { _ =>
          IO(println(s"importdata command '${command}' not recognized."))
        }
    }

}

object ImportDataActions {
  val ImportRecordsImportAction = "Microsoft.Dynamics.CRM.ImportRecordsImport"
  val ParseImportAction         = "Microsoft.Dynamics.CRM.ParseImport"
  val TransformImportAction     = "TransformImport"
  val BulkDeleteAction          = "BulkDelete"
}
