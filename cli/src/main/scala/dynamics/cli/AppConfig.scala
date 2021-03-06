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
    officeConfigFile: Option[String] = None,
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
  /** Also obtaned from the environment, normally. */
    impersonate: Option[String] = None,
)

@Lenses case class PluginConfig(
    source: Option[String] = None,
    watch: Boolean = false
)

@Lenses case class ThemesConfig(
    source: Option[String] = None,
    target: Option[String] = None,
    webresourceName: Option[String] = None,
    mergeFile: Option[String] = None,
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

@Lenses case class DeduplicationConfig(
  /** Names or ids. */
  identifiers: Seq[String] = Nil,
)

@Lenses case class ImportConfig(
    uploadFilename: Seq[String] = Nil,
    importMapNoClobber: Boolean = false,
    importDataInputFile: String = "",
    mapName: String = "",
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
  includeGlobal: Boolean = true,
  includeLocal: Boolean = true,
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
    upsert: Boolean = false,
    entity: String = "",
    pk: Option[String] = Some("id"),
    upsertPreventCreate: Boolean = true,
    upsertPreventUpdate: Boolean = false,
    renames: Seq[(String, String)] = Nil,
    keeps: Seq[String] = Nil,
    drops: Seq[String] = Nil,
    drop: Option[Int] = None,
    take: Option[Int] = None,
  updateTransform: etl.Transform[js.Object, js.Object] = etl.Transform.identity[js.Object],
  configFile: Option[String] = None,
  config: Option[UpdateProcessingConfig] = None,

  /** Query to run a "update one property" process on. */
  query: Option[String] = None,
  /** Skip updating the target attribute if the source is null. */
  skipIfNull: Boolean = true,
  source: Option[String] = None,
  target: Option[String] = None,
  value: Option[String] = None,
)

@Lenses case class ETLConfig(
    name: String = "", // name of etl program to run
    paramsFile: Option[String] = None,
    dataInputFile: String = "",
    verbosity: Int = 0,
    take: Option[Long] = None,
    drop: Option[Long] = None,
    maxPageSize: Option[Int] = None,
    cliParameters: Map[String, String] = Map(),
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
  /** Could be id or UPN... */
    userid: Option[String] = None,
  roleNames: Seq[String] = Nil,
  userQuery: Option[String] = None,
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

/**
  * A simple cake to bundle together a data structure for capturing
  * CLI parameters and a CLI parser that can parse parameters.
  *
  * @todo: Should probably bundle together a `run` method to run the program.
  */
trait CLIApplication {

  type AppConfig <: AppConfigLike

  trait AppConfigLike {
    def common: CommonConfig
  }

  /** Adds are a thunk to keep this pure. */
  def addParserOpts: Seq[scopt.OptionParser[AppConfig] => Unit]

  /** Add version and help options. */
  protected def addBasicParserOpts(op: scopt.OptionParser[AppConfig]): Unit = {
    import op._
    head("dynamics", BuildInfo.version)
    version("version")
      .abbr("v")
      .text("Print version")
    help("help")
      .abbr("h")
      .text("dynamics command line tool")
  }
}

/**
  * Standard operations for a basic CLI.
  */
trait StandardCLIApplication extends CLIApplication {

  type AppConfig <: AppConfigLike

  trait AppConfigLike extends super.AppConfigLike {
    def solution: SolutionConfig
    def export: ExportConfig
    def update: UpdateConfig
    def etl: ETLConfig
    def workflow: WorkflowConfig
    def plugin: PluginConfig
    def importdata: ImportConfig
    def webResource: WebResourceConfig
    def metadata: MetadataConfig
    def sdkMessage: SDKMessageConfig
    def test: TestConfig
    def systemjob: SystemJobConfig
    def action: ActionConfig
    def settings: SettingsConfig
    def appModule: AppModuleConfig
    def token: TokenConfig
    def user: UserConfig
    def themes: ThemesConfig
  }
}

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
    user: UserConfig = UserConfig(),
  themes: ThemesConfig = ThemesConfig(),
  deduplication: DeduplicationConfig = DeduplicationConfig(),
) {
  def debug       = common.debug
  def noisy       = !common.quiet
  def command     = common.command
  def subcommand  = common.subcommand
  def loggerLevel = common.loggerLevel
  def connectInfo = common.connectInfo
}
