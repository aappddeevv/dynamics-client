// Copyright (c) 2017 aappddeevv@gmail.com
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package cli

import scala.scalajs.js
import scala.concurrent.duration._
import cats.data._
import monocle.macros.Lenses

import dynamics.http.ConnectionInfo
import cats.implicits._
import dynamics.common._

@Lenses case class CommonConfig(
    lcid: Int = 1033,
    debug: Boolean = false,
    loggerLevel: Option[String] = Some("error"),
    quiet: Boolean = false,
    verbose: Boolean = false,
    crmConfigFile: String = "dynamics.json",
    outputDir: String = "./",
    outputFile: Option[String] = None,
    logFile: String = "dynamicscli.log",
    noclobber: Boolean = false,
    numRetries: Int = 5,
    pauseBetween: FiniteDuration = 10.seconds,
    command: String = "", // should rename to "command"
    subcommand: String = "",
    filter: Seq[String] = Nil,
    /** Connection information. This is set at the start so null is save here. */
    connectInfo: ConnectionInfo = null,
    createDirectories: Boolean = true,
    tableFormat: String = "ramac",
    fields: Option[Seq[String]] = None,
    requestTimeOutInMillis: Option[Int] = None,
    concurrency: Int = 4,
    batchSize: Int = 10,
    metadataCacheFile: Option[String] = None,
    ignoreMetadataCache: Boolean = false,
    /** NOT USED: Override to provide your own actions to be executed. */
    actionSelector: Option[ActionSelector] = Some(MainHelpers.defaultActionSelector)
)

@Lenses case class PluginConfig(
    source: Option[String] = None,
    watch: Boolean = false
)

@Lenses case class WebResourceConfig(
    webResourceDumpRawOutputFile: String = null,
    webResourceDownloadStripPrefix: Boolean = false,
    webResourceUploadSource: Seq[String] = Nil,
    webResourceUploadPrefix: Option[String] = None,
    webResourceUploadSolution: String = "Default",
    webResourceUploadPublish: Boolean = true,
    webResourceUploadNoclobber: Boolean = false,
    webResourceUploadRegister: Boolean = true,
    webResourceUploadType: Option[String] = None,
    webResourceUploadWatch: Boolean = false,
    webResourceUploadWatchIgnore: Seq[String] = Nil,
    webResourceDeleteNameRegex: Seq[String] = Nil,
    webResourceDeletePublish: Boolean = true
)

@Lenses case class SystemJobConfig(
    )

@Lenses case class SolutionConfig(
    solutionUploadFile: String = null,
    solutionName: String = null,
    solutionExportManaged: Boolean = false,
    solutionJsonConfigFile: Option[String] = None,
    solutionPublishWorkflows: Boolean = true
)

@Lenses case class ImportConfig(
    importMapUploadFilename: Seq[String] = Nil,
    importMapNoClobber: Boolean = false,
    importDataInputFile: String = "",
    importDataImportMapName: String = "",
    //importDataImportMapFilename: Option[String] = None,
    importDataName: Option[String] = None,
    importDataPollingInterval: Int = 60, // in seconds
    importDataEnableDuplicateDetection: Boolean = false,
    importDataCreate: Boolean = true, // if false, then update
    importDataDeleteJobName: String = "",
    importDataDeleteImportId: String = "",
    importDataDeleteRecurrencePattern: Option[String] = None,
    importDataDeleteStartTime: Option[String] = None,
    importDataDeleteQueryJson: Option[String] = None,
    importDataResumeImportId: String = "",
    importDataResumeImportFileId: String = ""
)

@Lenses case class MetadataConfig(
    )

@Lenses case class ExportConfig(
    exportEntity: String = "",
    exportFilter: Option[String] = None,
    exportSelect: Seq[String] = Nil,
    exportOrderBy: Seq[String] = Nil,
    exportTop: Option[Int] = None,
    exportSkip: Option[Int] = None,
    exportIncludeCount: Boolean = false,
    exportRaw: Boolean = false,
    exportFetchXml: Option[String] = None,
    exportMaxPageSize: Option[Int] = None,
    exportIncludeFormattedValues: Boolean = false,
    entityQuery: String = "",
    exportWrap: Boolean = false,
    exportRepeat: Boolean = false
)

@Lenses case class UpdateConfig(
    updateDataInputCSVFile: String = "",
    updateUpsert: Boolean = false,
    updateEntity: String = "",
    updatePKColumnName: String = "id",
    upsertPreventCreate: Boolean = true,
    upsertPreventUpdate: Boolean = false,
    updateRenames: Seq[(String, String)] = Nil,
    updateKeeps: Seq[String] = Nil,
    updateDrops: Seq[String] = Nil,
    updateDrop: Option[Int] = None,
    updateTake: Option[Int] = None,
    updateTransform: etl.Transform[js.Object, js.Object] = etl.Transform.identity[js.Object]
)

@Lenses case class ETLConfig(
    etlName: String = "", // name of etl program to run
    etlParamsFile: Option[String] = None,
    etlDataInputFile: String = "",
    etlVerbosity: Int = 0,
    etlBatchSize: Int = 10,
    etlTake: Option[Int] = None,
    etlDrop: Option[Int] = None,
    etlMaxPageSize: Option[Int] = None,
    etlCLIParameters: Map[String, String] = Map(),
    etlBatch: Boolean = false,
    etlDryRun: Boolean = false
)

@Lenses case class SDKMessageConfig(
    id: String = "",
)

@Lenses case class TestConfig(
    testArg: Option[String] = None,
)

@Lenses case class WorkflowConfig(
    workflowODataQuery: Option[String] = None,
    workflowPkName: String = "",
    workflowBatch: Boolean = false,
    workflowCache: Boolean = false,
    workflowCacheFilename: Option[String] = None,
    workflowActivate: Boolean = true,
    workflowIds: Seq[String] = Nil,
    workflowNames: Seq[String] = Nil,
    workflowActivateFilterName: Option[String] = None
)

@Lenses case class TokenConfig(
    tokenOutputFile: String = "crm-token.json"
)

@Lenses case class AppConfig(
    common: CommonConfig = CommonConfig(),
    solution: SolutionConfig = SolutionConfig(),
    export: ExportConfig = ExportConfig(),
    update: UpdateConfig = UpdateConfig(),
    etl: ETLConfig = ETLConfig(),
    workflow: WorkflowConfig = WorkflowConfig(),
    plugin: PluginConfig = PluginConfig(),
    importdata: ImportConfig = ImportConfig(),
    webResource: WebResourceConfig = WebResourceConfig(),
    metadata: MetadataConfig = MetadataConfig(),
    sdkMessage: SDKMessageConfig = SDKMessageConfig(),
    test: TestConfig = TestConfig(),
    systemjob: SystemJobConfig = SystemJobConfig()
) {
  def debug       = common.debug
  def noisy       = !common.quiet
  def command     = common.command
  def subcommand  = common.subcommand
  def loggerLevel = common.loggerLevel
  def connectInfo = common.connectInfo
}
