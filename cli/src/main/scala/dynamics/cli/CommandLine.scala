// Copyright (c) 2017 aappddeevv@gmail.com
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package cli

import scala.scalajs._
import js._
import annotation._
import io.scalajs.nodejs
import io.scalajs.nodejs._
import fs._
import scala.concurrent._
import duration._
import js.DynamicImplicits
import js.Dynamic.{literal => lit}
import scala.util.{Try, Success, Failure}
import fs2._
import fs2.util._
import scala.concurrent.duration._
import io.scalajs.npm.winston
import io.scalajs.npm.winston._
import io.scalajs.npm.winston.transports._
import cats.implicits._

import dynamics.common._
import dynamics.client._
import dynamics.http._
import Status._
import Utils._

object CommandLine {
  import scopt._

  type OptionProvider[T] = scopt.OptionParser[T] => Unit

  def mkParser(name: String = "dynamics") = new scopt.OptionParser[AppConfig](name) {
    //override def showUsageOnError = true
    override def terminate(exitState: Either[String, Unit]): Unit = {
      process.exit()
    }
  }

  def general(op: scopt.OptionParser[AppConfig]): Unit = {
    import op._

    opt[Unit]("debug")
      .text("Debug level logging")
      .hidden()
      .action((x, c) => c.copy(debug = true))
    opt[Int]("concurrency").text("General concurrency metric. Default is 4.").action((x, c) => c.copy(concurrency = x))
    opt[String]("logger-level")
      .hidden()
      .text("Logger level: trace, debug, info, warn or error. Overrides debug option.")
      .action { (x, c) =>
        val newC = c.copy(loggerLevel = Some(x))
        if (x.toUpperCase == "DEBUG") newC.copy(debug = true)
        else newC
      }
    opt[Int]("lcid")
      .text("Whenever language choices need to be made, use this LCID.")
      .action((x, c) => c.copy(lcid = x))
    opt[String]("logfile")
      .text("Logger file.")
      .action((x, c) => c.copy(logFile = x))
    opt[Unit]('v', "verbose")
      .text("Be verbose.")
    opt[Unit]('q', "quiet")
      .text("No extra output, just the results of the command.")
      .action((x, c) => c.copy(quiet = true))
    opt[String]('c', "crm-config")
      .valueName("<file>")
      .text("CRM connection configuration file")
      .action { (x, c) =>
        c.copy(crmConfigFile = x)
      }
    opt[String]("table-format")
      .valueName("honeywell|norc|ramac|void")
      .text("Change the table output format. void = no table adornments.")
      .action((x, c) => c.copy(tableFormat = x))
    opt[Int]("num-retries")
      .text("Number of retries if a request fails. Default is 5.")
      .action((x, c) => c.copy(numRetries = x))
    opt[Int]("pause-between")
      .text("Pause between retries in seconds. Default is 10.")
      .action((x, c) => c.copy(pauseBetween = x.seconds))
      .validate(pause =>
        if (pause < 0 || pause > 60 * 5) failure("Pause must be between 0 and 300 seconds.") else success)
    opt[Int]("request-timeout")
      .text("Request timeout in millis. 1000millis = 1s")
      .action((x, c) => c.copy(requestTimeOutInMillis = Some(x)))
    opt[String]("metadata-cache-file")
      .text("Metadata cache file to use explicitly. Otherwise it is automatically located.")
      .action((x, c) => c.copy(metadataCacheFile = Some(x)))
    opt[Unit]("ignore-metadata-cache")
      .text("Ignore any existing metadata cache. This will cause a new metadata download.")
      .action((x, c) => c.copy(ignoreMetadataCache = true))
    opt[Int]("batchsize").text("If batching is used, this is the batch size.").action((x, c) => c.copy(batchSize = x))
    opt[String]("outputdir")
      .text("Output directory for any content output.")
      .action((x, c) => c.copy(outputDir = x))
    opt[String]("outputfile")
      .text("Output file.")
      .action((x, c) => c.copy(outputFile = Some(x)))

  }

  case class CliHelpers(protected val op: scopt.OptionParser[AppConfig]) {
    import op._

    def newline(n: Int = 1): Unit = (0 to n).foreach(_ => note("\n"))

    val mkOutputDir = () =>
      opt[String]("outputdir")
        .text("Output directory for downloads.")
        .action((x, c) => c.copy(outputDir = x))

    val mkNoClobber = () =>
      opt[Unit]("noclobber").text("Do not overwrite existing file.").action((x, c) => c.copy(noclobber = true))

    val mkFilterOptBase = () =>
      opt[Seq[String]]("filter")
        .unbounded()
        .action((x, c) => c.copy(filter = c.filter ++ x))

    val mkFilterOpt = () =>
      opt[Seq[String]]("filter")
        .unbounded()
        .text("Filter Web Resources with a regex. --filter can be repeated.")
        .action((x, c) => c.copy(filter = c.filter ++ x))

    val mkFields = () =>
      opt[Seq[String]]("fields")
        .text("List of fields to retrieve. Use the Dynamics logical name.")
        .action((x, c) => c.copy(fields = Some(x)))
  }

  def metadata(op: scopt.OptionParser[AppConfig]): Unit = {
    import op._
    cmd("metadata")
      .text("Report out on metadata.")
      .action((x, c) => c.copy(command = "metadata"))
      .children(
        cmd("list-entities").text("List all entities.").action((x, c) => c.copy(subcommand = "listentities")),
        cmd("download-csdl")
          .text("Download CSDL.")
          .action((x, c) => c.copy(subcommand = "downloadcsdl"))
          .children(
            arg[String]("output-file")
              .text("Output CSDL file.")
              .action((x, c) => c.copy(metadataDownloadOutputFile = x))
          ),
        cmd("test").text("Run a metadata test.").action((x, c) => c.copy(subcommand = "test"))
      )
    note("\n")
    note("Metadata download files are large and may take a while.")
  }

  def whoami(op: scopt.OptionParser[AppConfig]): Unit = {
    import op._
    cmd("whoami")
      .text("Print out WhoAmI information.")
      .action((x, c) => c.copy(command = "whoami"))
  }

  def token(op: scopt.OptionParser[AppConfig]): Unit = {
    import op._

    cmd("token")
      .text("Manage tokens.")
      .action((x, c) => c.copy(command = "token"))
      .children(
        cmd("get-one")
          .text("Get one token and output to a file.")
          .action((x, c) => c.copy(subcommand = "getOne"))
          .children(
            opt[String]("outputfile").text("Output file. Will overwrite.").action((x, c) => c.copy(tokenOutputFile = x))
          ),
        cmd("get-many")
          .text("Get a token and output it as json to a file. Renew and overwrite automatically.")
          .action((x, c) => c.copy(subcommand = "getMany"))
          .children(
            opt[String]("outputfile").text("Output file. Will overwrite.").action((x, c) => c.copy(tokenOutputFile = x))
          )
      )
    note("Default token output file is crm-token.json.")
  }

  def update(op: scopt.OptionParser[AppConfig]): Unit = {
    import op._
    val helpers = CliHelpers(op)
    import helpers._

    cmd("update")
      .text(
        "Update dynamics records. An updated can also be an insert. Attribute processing order is drops, renames then keeps.")
      .action((x, c) => c.copy(command = "update"))
      .children(
        arg[String]("entity")
          .text("Entity to update. Use the entity logical name which is usually lowercase.")
          .action((x, c) => c.copy(updateEntity = x)),
        arg[String]("inputfile")
          .text("CSV data file.")
          .action((x, c) => c.copy(updateDataInputCSVFile = x)),
        opt[Boolean]("upsertpreventcreate")
          .text("Prevent a create if the record to update is not present. Default is true.")
          .action((x, c) => c.copy(upsertPreventCreate = x)),
        opt[Boolean]("upsertpreventupdate")
          .text("If you are inserting data using an update operation  and the record already exists, do not update. The default is false.")
          .action((x, c) => c.copy(upsertPreventUpdate = x)),
        opt[String]("pk")
          .text("Name of PK in the data input. Defaults to id (case insensitive.")
          .action((x, c) => c.copy(updatePKColumnName = x)),
        opt[Seq[String]]("drops")
          .text("Drop columns. Logical column names separate by commas. Can be specifed multiple times.")
          .action((x, c) => c.copy(updateDrops = c.updateDrops ++ x)),
        opt[Seq[String]]("keeps")
          .text("Keep columns. Logical column names separate by commas. Can be specifed multiple times.")
          .action((x, c) => c.copy(updateKeeps = c.updateKeeps ++ x)),
        opt[Int]("take").text("Process only N records.").action((x, c) => c.copy(updateTake = Some(x))),
        opt[Int]("drop").text("Drop first N records.").action((x, c) => c.copy(updateDrop = Some(x))),
        opt[Seq[String]]("renames")
          .text("Rename columns. Paris of oldname=newname, separate by commas. Can be specified multiple times.")
          .action { (x, c) =>
            val pairs = x.map(p => p.split('=')).map { a =>
              if (a.size != 2 || (a(0).size == 0 || a(1).size == 0))
                throw new IllegalArgumentException(s"Each rename pair of values must be separated by '=': $a.")
              (a(0), a(1))
            }
            c.copy(updateRenames = c.updateRenames ++ pairs)
          }
      )
    note("This is command is really only useful for updating/inserting data exported using the export command.")
    note(
      "Both configuration data or Dynamics entity in json format can be used. Processing performance is good even for large datasets.")
  }

  def entity(op: scopt.OptionParser[AppConfig]): Unit = {
    import op._
    val helpers = CliHelpers(op)
    import helpers._

    val formattedValues = opt[Unit]("include-formatted-values")
      .text("Include formmated values in the output. This increases the size significantly.")
      .action((x, c) => c.copy(exportIncludeFormattedValues = true))

    val top = opt[Int]("top")
      .text("Top N records.")
      .action((x, c) => c.copy(exportTop = Option(x)))

    val skip = opt[Int]("skip")
      .text("Skip N records.")
      .action((x, c) => c.copy(exportSkip = Option(x)))

    val maxPageSize = opt[Int]("maxpagesize")
      .text(
        "Set the maximum number of entities returned per 'fetch'. If node crashes when exporting large entities, set this smaller than 5000.")
      .action((x, c) => c.copy(exportMaxPageSize = Option(x)))

    cmd("entity")
      .text("Query/delete entity data from CRM using parts of a query that is assembled into a web api query.")
      .action((x, c) => c.copy(command = "entity"))
      .children(
        cmd("export")
          .text("Export entity data.")
          .action((x, c) => c.copy(subcommand = "export"))
          .children(
            arg[String]("entity")
              .text("Entity to export. Use the entity set collection logical name (the plural name) which is usually all lowercase and pluralized (s or es appended).")
              .action((x, c) => c.copy(exportEntity = x)),
            opt[String]("fetchxml")
              .text("Fetch XML for query focused on entity.")
              .action((x, c) => c.copy(exportFetchXml = Option(x))),
            top,
            skip,
            opt[String]("filter")
              .text("Filter criteria using web api format. Do not include $filter.")
              .action((x, c) => c.copy(exportFilter = Option(x))),
            opt[Seq[String]]("orderby")
              .text("Order by criteria e.g. createdon desc. Attribute must be included for download if you are using select. Multiple order bys can be specified in same option, separate by commas.")
              .action((x, c) => c.copy(exportOrderBy = x)),
            opt[Unit]("raw")
              .text("Instead of dumping a CSV file, dump one raw json record so you can see what attributes are available. Add a dummy select field to satisfy CLI args.")
              .action((x, c) => c.copy(exportRaw = true)),
            opt[Seq[String]]("select")
              .text("Select criteria using web api format. Do not include $select.")
              .action((x, c) => c.copy(exportSelect = x)),
            maxPageSize,
            formattedValues
          ),
        cmd("export-from-query")
          .text("Export entities from a raw web api query. Only raw json export is supported.")
          .action((x, c) => c.copy(subcommand = "exportFromQuery"))
          .children(
            arg[String]("query")
              .text("Web api format query string e.g. /contacts?$filter=...")
              .action((x, c) => c.copy(entityQuery = x)),
            opt[Unit]("wrap").text("Wrap the entire output in an array.").action((x, c) => c.copy(exportWrap = true)),
            formattedValues,
            maxPageSize,
            skip
          ),
        cmd("count")
          .text("Count entities. Concurrency affects how many counters run simultaneously.")
          .action((x, c) => c.copy(subcommand = "count"))
          .children(
            opt[Unit]("repeat").text("Repeat forever.").action((x, c) => c.copy(exportRepeat = true)),
            mkFilterOptBase().required().text("List of entity names to count. Use entity logical names.")
          ),
        cmd("delete-by-query")
          .text("Delete entities based on a query. This is very dangerous to use. Query must return primary key at the very least.")
          .action((x, c) => c.copy(subcommand = "deleteByQuery"))
          .children(
            arg[String]("query")
              .text("Web api format query string e.g. /contacts?$select=...&$filter=...")
              .action((x, c) => c.copy(entityQuery = x)),
            arg[String]("entity")
              .text("Entity to delete. We need this to identify the primary key")
              .action((x, c) => c.copy(exportEntity = x))
          )
      )
    note("""Skip must read records then skip them.""")
  }

  def importdata(op: scopt.OptionParser[AppConfig]): Unit = {
    import op._

    val helpers = CliHelpers(op)
    import helpers._

    cmd("importdata")
      .text("Import data using the CRM Data Import capability. Limited but still very useful.")
      .action((x, c) => c.copy(command = "importdata"))
      .children(
        cmd("list-imports")
          .text("List all imports.")
          .action((x, c) => c.copy(subcommand = "listimports")),
        cmd("list-importfiles")
          .text("List all import filess.")
          .action((x, c) => c.copy(subcommand = "listimportfiles")),
        cmd("bulkdelete")
          .text("Delete an import by the import id.")
          .action((x, c) => c.copy(subcommand = "bulkdelete"))
          .children(
            arg[String]("jobname")
              .text("Job name. This will appear in system jobs.")
              .action((x, c) => c.copy(importDataDeleteJobName = x)),
            arg[String]("importid")
              .text("Import id of import that loaded the data you want to delete.")
              .action((x, c) => c.copy(importDataDeleteImportId = x)),
            opt[String]("startat")
              .text("Start time in GMT offset. Use -5 for EST e.g. 2017-01-01T11:00:00Z is 6am EST. Default is 3 minutes from now.")
              .action((x, c) => c.copy(importDataDeleteStartTime = Option(x))),
            opt[String]("queryjson")
              .text("Query expression in json format. It should be an array: [{q1}, {q2},...]")
              .action((x, c) => c.copy(importDataDeleteQueryJson = Option(x))),
            opt[String]("recurrence")
              .text("Recurrence pattern using RFC2445. Default is to run once, empty string.")
              .action((x, c) => c.copy(importDataDeleteRecurrencePattern = Option(x)))
          ),
        cmd("delete")
          .text("Delete imports")
          .action((x, c) => c.copy(subcommand = "delete"))
          .children(
            mkFilterOpt().required()
          ),
        cmd("import")
          .text("Import data.")
          .action((x, c) => c.copy(subcommand = "import"))
          .children(
            arg[String]("<inputfile file>")
              .text("CSV data file.")
              .action((x, c) => c.copy(importDataInputFile = x)),
            arg[String]("<importmap file>")
              .text("Import map name, must already be loaded in CRM.")
              .action((x, c) => c.copy(importDataImportMapName = x)),
            opt[String]('n', "name")
              .text("Set the job name. A name is derived otherwise.")
              .action((x, c) => c.copy(importDataName = Option(x))),
            opt[Int]("polling-interval")
              .text("Polling interval in seconds for job completion tracking. Default is 60 seconds.")
              .action((x, c) => c.copy(importDataPollingInterval = x))
              .validate(x =>
                if (x < 1 || x > 60) failure("Polling frequency must be between 1 and 60 seconds.") else success),
            opt[Unit]("update")
              .text("Import data in update mode. Default is create mode. I don't think this works.")
              .action((x, c) => c.copy(importDataCreate = false)),
            opt[Boolean]("dupedetection")
              .text("Enable disable duplication detectiond. Default is disabled!.")
              .action((x, c) => c.copy(importDataEnableDuplicateDetection = x))
          ),
        cmd("resume")
          .text("Resume a data import at the last processing stage.")
          .action((x, c) => c.copy(subcommand = "resume"))
          .children(
            opt[String]("importid")
              .text("importid to resume on.")
              .required()
              .action((x, c) => c.copy(importDataResumeImportId = x)),
            opt[String]("importfileid")
              .text("importfiled to resume on.")
              .required()
              .action((x, c) => c.copy(importDataResumeImportFileId = x))
          )
      )
    note("\n")
    note("A log file may be created based on the import data file (NOT IMPLEMENTED YET).")
    note("You can upload an import map using the cli command 'importmap upload'.")
    note("If your cli is cutoff from the server, resume the processing using the 'importdata resume' command.")
    note("\n")
    note("For bulk delete query json syntax see: https://msdn.microsoft.com/en-us/library/mt491192.aspx")
  }

  def importmaps(op: scopt.OptionParser[AppConfig]): Unit = {
    import op._
    val helpers = CliHelpers(op)
    import helpers._

    cmd("importmaps")
      .text("Manage import maps")
      .action((x, c) => c.copy(command = "importmaps"))
      .children(
        note("\n"),
        cmd("list")
          .text("List import maps.")
          .action((x, c) => c.copy(subcommand = "list")),
        mkFilterOptBase().text("Filter on import map names and descriptions."),
        cmd("download")
          .text("Download import maps.")
          .action((x, c) => c.copy(subcommand = "download"))
          .children(
            mkNoClobber(),
            mkOutputDir()
          ),
        cmd("upload")
          .text("Upload a map.")
          .action((x, c) => c.copy(subcommand = "upload"))
          .children(
            arg[String]("<file>...")
              .text("Upload files.")
              .unbounded()
              .action((x, c) => c.copy(importMapUploadFilename = c.importMapUploadFilename :+ x)),
            opt[Unit]("noclobber")
              .text("Do not delete then upload map if it already exists. Default is false.")
              .action((x, c) => c.copy(importMapNoClobber = true))
          )
      )
  }

  def solutions(op: scopt.OptionParser[AppConfig]): Unit = {
    import op._
    cmd("solutions")
      .text("Manage solutions.")
      .action((x, c) => c.copy(command = "solutions"))
      .children(
        note("\n"),
        cmd("list")
          .text("List solutions")
          .action((x, c) => c.copy(subcommand = "list")),
        cmd("upload")
          .text("Upload a solution.")
          .action((x, c) => c.copy(subcommand = "upload"))
          .children(
            arg[String]("solution")
              .text("Solution file. Typically with . zip extension.")
              .action((x, c) => c.copy(solutionUploadFile = x)),
            opt[String]("config-file")
              .text("Config file in json format.")
              .action((x, c) => c.copy(solutionJsonConfigFile = Option(x))),
            opt[Unit]("skip-publishing-workflows")
              .text("Publish workflows.")
              .action((x, c) => c.copy(solutionPublishWorkflows = false))
          ),
        cmd("export")
          .text("Export a solution.")
          .action((x, c) => c.copy(subcommand = "export"))
          .children(
            arg[String]("solution")
              .text("Solution name.")
              .action((x, c) => c.copy(solutionName = x)),
            opt[String]("config-file")
              .text("Config file in json format.")
              .action((x, c) => c.copy(solutionJsonConfigFile = Option(x))),
            opt[Unit]("managed")
              .text("Export as managed solution.")
              .action((x, c) => c.copy(solutionExportManaged = true))
          ),
        cmd("delete")
          .text("Delete a solution")
          .action((x, c) => c.copy(subcommand = "delete"))
          .children(
            arg[String]("solution").text("Solution unique name").action((x, c) => c.copy(solutionName = x))
          )
      )
  }

  def publishers(op: scopt.OptionParser[AppConfig]): Unit = {
    import op._
    cmd("publishers")
      .text("Manage publishers.")
      .action((x, c) => c.copy(command = "publishers"))
      .children(
        note("\n"),
        cmd("list")
          .text("List publishers.")
          .action((x, c) => c.copy(subcommand = "list"))
      )
  }

  def systemjobs(op: scopt.OptionParser[AppConfig]): Unit = {
    import op._
    val helpers = CliHelpers(op)
    import helpers._

    cmd("systemjobs")
      .text("Manage system jobs.")
      .action((x, c) => c.copy(command = "asyncoperations"))
      .children(
        note("\n"),
        cmd("list")
          .text("List operations.")
          .action((x, c) => c.copy(subcommand = "list"))
          .children(mkFilterOpt()),
        cmd("delete-completed")
          .text("Delete completed system jobs.")
          .action((x, c) => c.copy(subcommand = "deleteCompleted")),
        cmd("delete-canceled")
          .text("Delete canceled system jobs.")
          .action((x, c) => c.copy(subcommand = "deleteCanceled")),
        cmd("delete-failed")
          .text("Delete failed system jobs.")
          .action((x, c) => c.copy(subcommand = "deleteFailed")),
        cmd("delete-waiting")
          .text("Delete waiting system jobs.")
          .action((x, c) => c.copy(subcommand = "deleteWaiting")),
        cmd("delete-waiting-for-resources")
          .text("Delete waiting system jobs.")
          .action((x, c) => c.copy(subcommand = "deleteWaitingForResources")),
        cmd("delete-inprogress")
          .text("Delete in-progress system jobs.")
          .action((x, c) => c.copy(subcommand = "deleteInProgress")),
        cmd("cancel")
          .text("Cancel some system jobs based on a name regex.")
          .action((x, c) => c.copy(subcommand = "cancel"))
          .children(
            mkFilterOpt().required()
          )
      )
  }

  def workflows(op: scopt.OptionParser[AppConfig]): Unit = {
    import op._
    val helpers = CliHelpers(op)
    import helpers._

    cmd("workflows")
      .text("Manage workflows.")
      .action((x, c) => c.copy(command = "workflows"))
      .children(
        note("\n"),
        cmd("list").text("List workflows.").action((x, c) => c.copy(subcommand = "list")).children(mkFilterOpt()),
        cmd("execute")
          .text("Execute a workflow against the results of a query. A cache can be used.")
          .action((x, c) => c.copy(subcommand = "execute"))
          .children(
            arg[String]("id")
              .text("Id of workflow to execute. Use the one with type=1 => template.")
              .action((x, c) => c.copy(workflowIds = c.workflowIds :+ x)),
            arg[String]("odataquery")
              .text("Use an OData query to select entities to execute against.")
              .action((x, c) => c.copy(workflowODataQuery = Some(x))),
            opt[String]("pk")
              .text("Id column name for the entity.")
              .required()
              .action((x, c) => c.copy(workflowPkName = x))
              .required(),
            opt[Unit]("batch").text("Run this batch.")
              action ((x, c) => c.copy(workflowBatch = true)),
            opt[Unit]("cache").text("Use a local cache.").action((x, c) => c.copy(workflowCache = true)),
            opt[String]("cache-file")
              .text("Cache file to use. Otherwise it is automatically named based on the pk.")
              .action((x, c) => c.copy(workflowCacheFilename = Some(x)))
          ),
        note("\n"),
        note("The ODataQuery should return the id of the entity e.g. '/contacts?$select=contactid'  --pk contactid."),
        note("\n"),
        cmd("change-activation")
          .text("Change activation of a workflow.")
          .action((x, c) => c.copy(subcommand = "changeactivation"))
          .children(
            opt[Seq[String]]("id")
              .text("Ids of workflow to activate. Obtain from using 'list'. Comma separated or repeat option.")
              .unbounded()
              .action((x, c) => c.copy(workflowIds = c.workflowIds ++ x)),
            //opt[Seq[String]]("names").
            //  text("Names of workflows. Comma separated or repeat option.").
            //  unbounded().
            //  action((x, c) => c.copy(workflowNames = c.workflowNames ++ x)),
            mkFilterOpt(),
            arg[Boolean]("activate")
              .text("true of false to activate/deactivate.")
              .action((x, c) => c.copy(workflowActivate = x))
          )
      )
    note("\n")
    note("A workflow is typically very slow to run. The best option is to not run it again if its already been run.")
    note("A simple cache can be optionally used to skip the entities that the workflow has already run against.")

  }

  def optionsets(op: scopt.OptionParser[AppConfig]): Unit = {
    import op._
    val helpers = CliHelpers(op)
    import helpers._

    cmd("optionsets")
      .text("OptionSet management.")
      .action((x, c) => c.copy(command = "optionsets"))
      .children(
        cmd("list")
          .text("List global option sets.")
          .action((x, c) => c.copy(subcommand = "list"))
          .children(mkFilterOpt())
      )
  }

  def sdkmessages(op: scopt.OptionParser[AppConfig]): Unit = {
    import op._
    val helpers = CliHelpers(op)
    import helpers._

    cmd("sdkmessages")
      .text("Manage SDK messages.")
      .action((x, c) => c.copy(command = "sdkmessageprocessingsteps"))
      .children(
        cmd("list")
          .text("List SDK messages processing step.")
          .action((x, c) => c.copy(subcommand = "list"))
          .children(mkFilterOpt()),
        cmd("deactivate")
          .text("Deactivate a SDK message by id or a filter.")
          .action((x, c) => c.copy(subcommand = "deactivate"))
          .children(
            opt[String]("crmid").text("Id to deactivate.").action((x, c) => c.copy(sdkMessagesId = x)),
            mkFilterOpt()
          ),
        cmd("activate")
          .text("Activate a SDK message by id or a filter.")
          .action((x, c) => c.copy(subcommand = "activate"))
          .children(
            opt[String]("crmid").text("Id to deactivate.").action((x, c) => c.copy(sdkMessagesId = x)),
            mkFilterOpt()
          )
      )
  }

  def plugins(op: scopt.OptionParser[AppConfig]): Unit = {
    import op._
    cmd("plugins")
      .text("Upload a plugin assembly's content.")
      .action((x, c) => c.copy(command = "plugins"))
      .children(
        cmd("upload")
          .text("Upload assembly (.dll) content to an existing plugin")
          .action((x, c) => c.copy(subcommand = "upload"))
          .children(
            arg[String]("source")
              .text("Plugin dll file location. Plugin name default is the file basename.")
              .action((x, c) => c.copy(pluginConfig = c.pluginConfig.copy(source = Some(x)))),
            opt[Unit]("watch")
              .text("Watch for changes and upload when the file is changed.")
              .action((x, c) => c.copy(pluginConfig = c.pluginConfig.copy(watch = true)))
          ))
    note("Assembly plugin registration information should be in [source name].json.")
    note("Currently, this function can only replace an existing plugin assembly's content.")
  }

  def webresources(op: scopt.OptionParser[AppConfig]): Unit = {
    import op._
    val helpers = CliHelpers(op)
    import helpers._

    cmd("webresources")
      .text("Manage Web Resources.")
      .action((x, c) => c.copy(command = "webresources"))
      .children(
        note("\n"),
        cmd("list")
          .text("List web resources")
          .action((x, c) => c.copy(subcommand = "list"))
          .children(
            mkFilterOpt()
          ),
        note("\n"),
        cmd("delete")
          .text("Delete a webresource by its unique name.")
          .action((x, c) => c.copy(subcommand = "delete"))
          .children(
            mkFilterOpt(),
            opt[Unit]("nopublish")
              .text("Do not publish the deletion. Default is to publish.")
              .action((x, c) => c.copy(webResourceDeletePublish = false)),
            arg[String]("name")
              .text("Regex for Web Resource unique names. Can be repeated. Be very careful with your regexs.")
              .action((x, c) => c.copy(webResourceDeleteNameRegex = c.webResourceDeleteNameRegex :+ x))
          ),
        note("\n"),
        cmd("download")
          .text("Download Web Resources and save them to the filesystem. If there are path separators in the name, create filesystem directories as needed.")
          .action((x, c) => c.copy(subcommand = "download"))
          .children(
            mkFilterOpt(),
            mkOutputDir(),
            /*
             opt[Unit]("nodirs")
             .text("Place all downloaded files at the same level in the output directory.")
             .action((x, c) => c.copy(createDirectories = false)),
             */
            mkNoClobber(),
            /*
             opt[Unit]("strip-prefix")
             .text("Strip leading prefix in the first file segment if it ends with _ from all downloaded web resources. This removes the solution prefix.")
             .action((x,c) => c.copy(webResourceDownloadStripPrefix =true))
           */
          ),
        note("\n"),
        cmd("upload")
          .text("Upload web resources and optionally publish.")
          .action((x, c) => c.copy(subcommand = "upload"))
          .children(
            arg[String]("source")
              .unbounded()
              .text("Globs for Web Resource(s). To pull an entire directory, use dir/* where dir is usually a publisher prefix.")
              .action((x, c) => c.copy(webResourceUploadSource = c.webResourceUploadSource :+ x)),
            opt[String]("prefix")
              .text("Give a source file as a path locally, Use prefix to identify the start of the resource name--almost always the publisher prefix.")
              .action((x, c) => c.copy(webResourceUploadPrefix = Option(x))),
            opt[Unit]("nopublish")
              .text("Do not publish the uploaded Web Resources after uploading. Default is to publish.")
              .action((x, c) => c.copy(webResourceUploadPublish = false)),
            opt[Unit]("noclobber")
              .text("Do not overwrite existing Web Resources. Default is false, overwrite.")
              .action((x, c) => c.copy(webResourceUploadNoclobber = true)),
            opt[String]('s', "unique-solution-name")
              .text("Solution unique name for new resources that are registered. Solution must already exist. Defaults to 'Default' if none provided.")
              .action((x, c) => c.copy(webResourceUploadSolution = x)),
            opt[Unit]("noregister")
              .text("Do not register a new Web Resource with the solution if the resource does not already exist. Default is it register.")
              .action((x, c) => c.copy(webResourceUploadRegister = false)),
            opt[String]('t', "type")
              .text("Resource type 'extension' (e.g. js or png) if not inferrable from resource name. Will override the filenames prefix for all resources to upload that must be created.")
              .action((x, c) => c.copy(webResourceUploadType = Option(x))),
            opt[Unit]("watch")
              .text("Watch source for changes and upload changed files.")
              .action((x, c) => c.copy(webResourceUploadWatch = true)),
            opt[String]("ignore")
              .text("When in watch mode, a regex patterns to ignore when watching. Can be repeated.")
              .unbounded()
              .action((x, c) => c.copy(webResourceUploadWatchIgnore = c.webResourceUploadWatchIgnore :+ x))
          ),
        note("\n"),
        note(
          "Sources are glob patterns. If the glob expands to a single resource, the source points to the resource directly and target is the full path in dynamics. Web Resources actually have a flat namespace so directories become slashes in the final name. Web Resources with spaces or hyphens in their names cannot be uploaded. New Web Resources must be associated a solution. Use --prefix to indicate where a OS path should be split in order to generate the resource name which always starts with a publisher prefix. Given a OS path of /tmp/new_/blah.js, --prefix new_ will compute a resource name of new/blah.js. If the resource is /tmp/new_blah.js, --prefix new_ or no prefix will use only the filename (basename.prefix) as the resource name. A webresource cannot be deleted if it is being used, you will get a server error. Remove the webresoruce as a dependency then try to delete it.")
      )
  }

  def dumpraw(op: scopt.OptionParser[AppConfig]): Unit = {
    import op._
    val helpers = CliHelpers(op)
    import helpers._

    cmd("dumpraw")
      .text("Dump the raw JSON response for a Web Resource")
      .action((x, c) => c.copy(subcommand = "dumpraw"))
      .children(
        mkFilterOpt(),
        arg[String]("outputfile")
          .text("Output file")
          .action((x, c) => c.copy(webResourceDumpRawOutputFile = x))
      )
  }

  /** Command line parser using scopt. */
  def addAllOptions(op: scopt.OptionParser[AppConfig]): Unit = {
    import op._
    val helpers = CliHelpers(op)
    import helpers._

    head("dynamics", "0.1.0")
    general(op);
    note("\n")
    metadata(op)
    note("\n")
    whoami(op)
    note("\n")
    token(op)
    note("\n")
    update(op)
    note("\n")
    entity(op)
    note("\n")
    importdata(op)
    note("\n")
    importmaps(op)
    note("\n")
    solutions(op)
    note("\n")
    publishers(op)
    note("\n")
    systemjobs(op)
    note("\n")
    workflows(op)
    note("\n")
    optionsets(op)
    note("\n")
    sdkmessages(op)
    note("\n")
    webresources(op)
    note("\n")
    dumpraw(op)
    note("\n")
    plugins(op)
    note("\n")
    help("help").text("dynamics command line tool")

    opt[Unit]("test")
      .text("Run some tests...secretly")
      .hidden()
      .action((x, c) => c.copy(command = "__test__"))
      .children(
        opt[String]("arg")
          .text("arg to test routine")
          .action((x, c) => c.copy(testArg = Some(x)))
      )
  }

  /** Read connection info from file in json format.
    * If there is an error, cannot read the file or if no password in the
    * config file or the enviroment variable DYNAMICS_PASSWORD,
    * return error string.
    */
  def readConnectionInfo(file: String): Either[String, ConnectionInfo] = {
    import scala.util.{Try, Success, Failure}
    Try { slurpAsJson[ConnectionInfo](file) } match {
      case Success(ci) =>
        // look for password in env variable
        val envpassword = nodejs.process.env.get("DYNAMICS_PASSWORD")
        // use env password if it is not in the config file
        (envpassword orElse ci.password.toOption).fold[Either[String, ConnectionInfo]](
          Left(s"No password found in environment variable DYNAMICS_PASSWORD or config file ${file}.")) { password =>
          ci.password = js.defined(password)
          Right(ci)
        }
      case Failure(t) =>
        Left(s"Unable to read configration file ${file}. ${t.getMessage()}")
    }
  }

  def readConnectionInfoOrExit(file: String): ConnectionInfo = {
    readConnectionInfo(file) match {
      case Right(c) => c
      case Left(e) =>
        println(e)
        process.exit(1)
        null
    }
  }

}
