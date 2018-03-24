// Copyright (c) 2017 The Trapelo Group LLC
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
    verbosity: Int = 0,
    crmConfigFile: Option[String] = None,
    outputDir: String = "./",
    outputFile: Option[String] = None,
    logFile: String = "dynamicscli.log",
    noclobber: Boolean = false,
  numRetries: Int = 5,
  retryPolicy: String = "backoff", // pause, backoff
    pauseBetween: FiniteDuration = 10.seconds, // or initial value if backoff
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
    batch: Boolean = false,
    metadataCacheFile: Option[String] = None,
    ignoreMetadataCache: Boolean = false,
  actionSelector: Option[ActionSelector] = Some(MainHelpers.defaultActionSelector),
  impersonate: Option[String] = None,
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

@Lenses case class ActionConfig(
    action: String = "",
    payloadFile: Option[String] = None,
    pprint: Boolean = false,
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
  importDataResumeImportFileId: String = "",
    recordsOwnerId: Option[String] = None,
)

@Lenses case class MetadataConfig(
    )

@Lenses case class ExportConfig(
    entity: String = "",
    filter: Option[String] = None,
    select: Seq[String] = Nil,
    orderBy: Seq[String] = Nil,
    top: Option[Int] = None,
    skip: Option[Int] = None,
    includeCount: Boolean = false,
    raw: Boolean = false,
    fetchXml: Option[String] = None,
    maxPageSize: Option[Int] = None,
    includeFormattedValues: Boolean = false,
    query: String = "",
    queries: Map[String, String] = Map.empty,
    queryFile: Option[String] = None,
    wrap: Boolean = false,
    repeat: Boolean = false,
  repeatDelay: Int = 60,
  header: Boolean = true,
  useFunction: Boolean = false,
)

@Lenses case class UpdateConfig(
    inputFile: String = "",
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
    name: String = "", // name of etl program to run
    paramsFile: Option[String] = None,
    dataInputFile: String = "",
    verbosity: Int = 0,
    batchSize: Int = 10,
    take: Option[Long] = None,
    drop: Option[Long] = None,
    maxPageSize: Option[Int] = None,
    cliParameters: Map[String, String] = Map(),
    batch: Boolean = false,
    dryRun: Boolean = false,
    /** Query to run, if relevant. */
    query: Option[String] = None,
    /** File that holds a query to run, if relevant. */
    queryFile: Option[String] = None,
    connectionUrl: Option[String] = None,
    connectionFile: Option[String] = None,
) {
  val defaultConnectionFile = "sqlserver-connection.json"

  def connectionFileOrDefault: String = connectionFile.getOrElse(defaultConnectionFile)
}

@Lenses case class SDKMessageConfig(
    id: String = "",
)

@Lenses case class UserConfig(
  userid: Option[String] = None,
  roleNames: Seq[String] = Nil
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
    activate: Boolean = true,
    workflowIds: Seq[String] = Nil,
    workflowNames: Seq[String] = Nil,
    workflowActivateFilterName: Option[String] = None
)

@Lenses case class AppModuleConfig(
  roleName: Seq[String] = Nil,
  change: Option[String] = None,
  appName: Option[String] = None,
)

@Lenses case class TokenConfig(
  tokenOutputFile: String = "crm-token.json",
  refreshIntervalInMinutes: Int = 55,
)

@Lenses case class SettingsConfig(
    settingsFile: Option[String] = None,
    entityList: Seq[String] = Nil,
    name: Option[String] = None,
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
    systemjob: SystemJobConfig = SystemJobConfig(),
    action: ActionConfig = ActionConfig(),
  settings: SettingsConfig = SettingsConfig(),
  appModule: AppModuleConfig = AppModuleConfig(),
  token: TokenConfig = TokenConfig(),
  user: UserConfig = UserConfig()
) {
  def debug       = common.debug
  def noisy       = !common.quiet
  def command     = common.command
  def subcommand  = common.subcommand
  def loggerLevel = common.loggerLevel
  def connectInfo = common.connectInfo
}
