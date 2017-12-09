// Copyright (c) 2017 aappddeevv@gmail.com
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package cli

import scala.scalajs.js
import scala.concurrent.duration._
import cats.data._

import dynamics.http.ConnectionInfo
import cats.implicits._
import dynamics.common._

case class CommonConfig(
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
    connectInfo: ConnectionInfo = null,
    createDirectories: Boolean = true,
    tableFormat: String = "ramac",
    fields: Option[Seq[String]] = None,
    requestTimeOutInMillis: Option[Int] = None,
    concurrency: Int = 4,
    batchSize: Int = 10,
    metadataCacheFile: Option[String] = None,
    ignoreMetadataCache: Boolean = false,
    /** Override to provide your own actions to be executed. */
    actionSelector: Option[ActionSelector] = Some(MainHelpers.defaultActionSelector)
) {
  val noisy = !quiet
}
trait Common
object Common {
  //val fromCommonConfig: Kleisli[Option, CommonConfig, Common]
}

case class PluginConfig(
    source: Option[String] = None,
    watch: Boolean = false
)
trait Plugin
object Plugin {
  //val fromPluginConfig: Kleisli[Option, PluginConfig, Plugin] = Some(_)
}

case class WebResourceConfig(
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
case class AsyncOperationConfig(
    asyncOperationDeletePublish: Boolean = true
)

case class SolutionConfig(
    solutionUploadFile: String = null,
    solutionName: String = null,
    solutionExportManaged: Boolean = false,
    solutionJsonConfigFile: Option[String] = None,
    solutionPublishWorkflows: Boolean = true
)
case class ImportConfig(
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
case class MetadataConfig(
    metadataDownloadOutputFile: String = ""
)

case class ExportConfig(
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

case class UpdateConfig(
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
case class ETLConfig(
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

case class SDKMessagesConfig(
    sdkMessagesId: String = "",
)

case class TestConfig(
    testArg: Option[String] = None,
    testConcurrency: Int = 4
)

case class WorkflowConfig(
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

case class TokenConfig(
    tokenOutputFile: String = "crm-token.json"
)

case class AppConfig2(
    common: CommonConfig,
    webresource: WebResourceConfig,
    asyncOp: AsyncOperationConfig,
    solution: SolutionConfig,
    importMap: ImportConfig,
    metadata: MetadataConfig,
    export: ExportConfig,
    update: UpdateConfig,
    etl: ETLConfig,
    sdkMessage: SDKMessagesConfig,
    test: TestConfig,
    workflow: WorkflowConfig,
    plugin: PluginConfig
)

case class AppConfig(
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
    connectInfo: ConnectionInfo = null,
    createDirectories: Boolean = true,
    tableFormat: String = "ramac",
    fields: Option[Seq[String]] = None,
    requestTimeOutInMillis: Option[Int] = None,
    concurrency: Int = 4,
    batchSize: Int = 10,
    metadataCacheFile: Option[String] = None,
    ignoreMetadataCache: Boolean = false,
    /** Override to provide your own actions to be executed. */
    actionSelector: Option[ActionSelector] = Some(MainHelpers.defaultActionSelector),
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
    webResourceDeletePublish: Boolean = true,
    asyncOperationDeletePublish: Boolean = true,
    solutionUploadFile: String = null,
    solutionName: String = null,
    solutionExportManaged: Boolean = false,
    solutionJsonConfigFile: Option[String] = None,
    solutionPublishWorkflows: Boolean = true,
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
    metadataDownloadOutputFile: String = "",
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
    exportRepeat: Boolean = false,
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
    updateTransform: etl.Transform[js.Object, js.Object] = etl.Transform.identity[js.Object],
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
    etlDryRun: Boolean = false,
    sdkMessagesId: String = "",
    testArg: Option[String] = None,
    testConcurrency: Int = 4,
    workflowODataQuery: Option[String] = None,
    workflowPkName: String = "",
    workflowBatch: Boolean = false,
    workflowCache: Boolean = false,
    workflowCacheFilename: Option[String] = None,
    workflowActivate: Boolean = true,
    workflowIds: Seq[String] = Nil,
    workflowNames: Seq[String] = Nil,
    workflowActivateFilterName: Option[String] = None,
    tokenOutputFile: String = "crm-token.json",
    pluginConfig: PluginConfig = PluginConfig()
) {
  val noisy = !quiet
}
