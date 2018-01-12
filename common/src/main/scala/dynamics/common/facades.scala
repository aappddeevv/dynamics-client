// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package common

import scala.scalajs._
import js._
import annotation._
import io.scalajs.nodejs._
import io.scalajs.nodejs.buffer.Buffer
import io.scalajs.nodejs.fs._
import io.scalajs.util.PromiseHelper._
import scala.concurrent.Future
import io.scalajs.RawOptions
import io.scalajs.nodejs.stream.Writable
import io.scalajs.nodejs.events.IEventEmitter

class BulkDeleteAction(
    val JobName: String,
    val SourceImportId: js.UndefOr[String] = js.undefined,
    /** true, run synchronously, false otherwise. Always set this to false. */
    val RunNow: Boolean = false,
    /** Edm.DateTimeOffset: 2017-03-01T06:00:00Z - offset from GMT, subtract (-5) for EST */
    val StartDateTime: String,
    val SendEmailNotification: Boolean = false,
    /** Based on RFC2455. */
    val RecurrencePattern: String = "",
    val QuerySet: js.Array[js.Object] = js.Array(),
    val CCRecipients: js.Array[js.Object] = js.Array(),
    val ToRecipients: js.Array[js.Object] = js.Array()
) extends js.Object

@js.native
trait BulkDeleteResponse extends js.Object {
  val JobId: js.UndefOr[String] = js.native
}

class WebResourceUpsertArgs(
    val name: String,
    val displayname: String,
    val webresourcetype: Int,
    //  val isenabledformobileclient: Boolean = false,
    //  val iscustomizable: Boolean = true,
    val content: UndefOr[String] = js.undefined
) extends js.Object

@js.native
trait XmlElement extends js.Object {
  def eachChild(func: js.Function3[XmlElement, Double, js.Array[XmlElement], Unit]): Unit = js.native
  def childNamed(name: String): XmlElement                                                = js.native
  def childrenNamed(name: String): js.Array[XmlElement]                                   = js.native
  def childWithAttribute(name: String, value: String = js.native): XmlElement             = js.native
  def descendantWithPath(path: String): XmlElement                                        = js.native
  def valueWithPath(path: String): String                                                 = js.native
}

@js.native
@JSImport("xmldoc", "XmlDocument")
class XmlDocument() extends js.Object {
  def this(x: String) = this()
  def eachChild(func: js.Function3[XmlElement, Double, js.Array[XmlElement], Unit]): Unit = js.native
  def childNamed(name: String): XmlElement                                                = js.native
  def childrenNamed(name: String): js.Array[XmlElement]                                   = js.native
  def childWithAttribute(name: String, value: String = js.native): XmlElement             = js.native
  def descendantWithPath(path: String): XmlElement                                        = js.native
  def valueWithPath(path: String): String                                                 = js.native
}

@js.native
trait ImportMapOData extends js.Object {
  val name: String        = js.native
  val importmapid: String = js.native
  val description: String = js.native
  //@JSName("targetentity@OData.Community.Display.V1.FormattedValue")
  val targetentity: Int                     = js.native
  val importmaptype: js.UndefOr[Int]        = js.native
  val isvalidforimport: js.UndefOr[Boolean] = js.native
  val iswizardcreated: js.UndefOr[Boolean]  = js.native
  val mapcustamizations: js.UndefOr[String] = js.native
  val source: js.UndefOr[String]            = js.native
  val sourcetype: js.UndefOr[Int]           = js.native
  val statecode: js.UndefOr[Int]            = js.native
  val statuscode: js.UndefOr[Int]           = js.native
}

@js.native
trait ExportMappingsImportMapResponse extends js.Object {
  val MappingsXml: String = js.native
}

class ImportMappingsImportMap(
    val MappingsXml: String,
    val ReplaceIds: Boolean = true
) extends js.Object

@js.native
trait ImportLogJSON extends js.Object {
  val name: String = js.native
}

@js.native
@JSImport("prettyjson", JSImport.Namespace)
object PrettyJson extends js.Object {
  def render(data: js.Object, options: js.UndefOr[PrettyJsonOptions] = js.undefined): String = js.native
}

class PrettyJsonOptions(
    val noColor: js.UndefOr[Boolean] = js.undefined
) extends js.Object

@js.native
@JSImport("fs-extra", JSImport.Namespace)
object FsExtra extends js.Object {
  def mkdirs(path: String, callback: js.Function1[js.Error, Unit]): Unit = js.native

  def outputFile(file: String,
                 data: String | io.scalajs.nodejs.buffer.Buffer,
                 options: FileOutputOptions = null,
                 callback: FsCallback0): Unit = js.native
}

object Fse {
  def outputFile(file: String,
                 data: String | io.scalajs.nodejs.buffer.Buffer,
                 options: FileOutputOptions = null): Future[Unit] =
    promiseWithError0[FileIOError](FsExtra.outputFile(file, data, options, _))
}

@js.native
trait SolutionOData extends js.Object {
  var friendlyname: UndefOr[String]          = js.native
  var solutionid: UndefOr[String]            = js.native
  var _organizationid_value: UndefOr[String] = js.native
  var _publisherid_value: UndefOr[String]    = js.native
  var solutiontype: UndefOr[String]          = js.native
  var description: UndefOr[String]           = js.native
  var ismanaged: UndefOr[Boolean]            = js.native
  var uniquename: UndefOr[String]            = js.native
  var version: UndefOr[String]               = js.native
  var versionnumber: UndefOr[Long]           = js.native
}

@js.native
trait PublisherOData extends js.Object {
  val publisherid: String           = js.native
  val friendlyname: String          = js.native
  val description: String           = js.native
  val customizationprefix: String   = js.native
  val _organizationid_value: String = js.native
  val uniquename: String            = js.native
}

class ChangeAsyncJobState(
    val asyncoperationid: String,
    val statecode: Int
) extends js.Object

@js.native
trait AsyncOperationOData extends js.Object {
  val asyncoperationid: UndefOr[String] = js.native
  val name: UndefOr[String]             = js.native
  val startedon: UndefOr[String]        = js.native
  val statecode: UndefOr[Int]           = js.native
  val statuscode: UndefOr[Int]          = js.native
  val operationtype: UndefOr[Int]       = js.native
  val executiontimespan: UndefOr[Float] = js.native
}

object AsyncOperation {

  /** TODO: Get from server. */
  val StateCodes: Map[Int, String] = Map(0 -> "Ready", 1 -> "Suspended", 2 -> "Locked", 3 -> "Completed")

  /** TODO: Get from server. */
  val StatusCodes: Map[Int, String] = Map(
    // Ready
    0 -> "WaitingForResources",
    // Suspending
    10 -> "Waiting",
    // Locked
    20 -> "InProgress",
    21 -> "Pausing",
    22 -> "Canceling",
    // Completed
    30 -> "Succeeded",
    31 -> "Failed",
    32 -> "Canceled"
  )

  /** TODO: Get from server. */
  val OperationTypes: Map[Int, String] = Map(
    22 -> "Calculate Organization Maximum Storage Size",
    18 -> "Calculate Organization Storage Size",
    19 -> "Collect Organization Database Statistics",
    20 -> "Collection Organization Size Statistics",
    16 -> "Collect Organization Statistics",
    9  -> "SQM Data Collection",
    25 -> "Organization Full Text Catalog Index",
    31 -> "Storage Limit Notification",
    24 -> "Update Statistic Intervals",
    27 -> "Update Contract States"
  )

  /** Import files status code. */
  val ImportStatusCode: Map[Int, String] =
    Map(0 -> "Submitted", 1 -> "Parsing", 2 -> "Transforming", 3 -> "Importing", 4 -> "Completed", 5 -> "Failed")
}

/** Attaches to a solution. */
@js.native
trait SolutionComponentOData extends js.Object {

  /** 61=webresource */
  var solutioncomponentid: String    = js.native
  var rootsolutioncomonentid: String = js.native
  var objectid: String               = js.native
  var ismetadata: Boolean            = js.native
  var comonenttype: Int              = js.native
  var _solutionid_value: String      = js.native
}

/** WebResource as provided in odata response.
  */
@js.native
trait WebResourceOData extends js.Object {
  val name: String           = js.native
  val displayname: String    = js.native // can be null!2
  val webresourceid: String  = js.native
  val description: String    = js.native // can be null!
  val content: String        = js.native // base64 encoded, can be null
  val organizationid: String = js.native
  val solutionid: String     = js.native
  val canbedeleted: String   = js.native // boolean as string
  val version: String        = js.native // can be null
  val webresourcetype: Int   = js.native
  @JSName("webresourcetype@OData.Community.Display.V1.FormattedValue")
  val webresourcetypeF: String = js.native
  // 0 published, 1 unpublished, 2 deleted, 3 deleted unpublished
  val componentstate: Int = js.native
}

object WebResource {

  val typecodeToExtension = Map(1 -> "HTML",
                                2  -> "CSS",
                                3  -> "JS",
                                4  -> "XML",
                                5  -> "PNG",
                                6  -> "JPG",
                                7  -> "GIF",
                                8  -> "XAP",
                                9  -> "XSL",
                                10 -> "ICO",
                                11 -> "SVG",
                                12 -> "RESX")

  val allowedExtensions = Map(
    "css"   -> 2,
    "xml"   -> 4,
    "gif"   -> 7,
    "htm"   -> 1,
    "html"  -> 1,
    "ico"   -> 10,
    "jpg"   -> 6,
    "jpeg"  -> 6,
    "png"   -> 5,
    "js"    -> 3,
    "json"  -> 3,
    "map"   -> 3,
    "xap"   -> 8,
    "xsl"   -> 9,
    "slt"   -> 9,
    "eot"   -> 3,
    "svg"   -> 3,
    "ttf"   -> 3,
    "woff"  -> 3,
    "woff2" -> 3,
    "svg"   -> 11,
    "resx"  -> 12
  )
}

@js.native
@JSImport("process", JSImport.Namespace)
object processhack extends js.Object {
  def hrtime(previous: UndefOr[Array[Int]] = js.undefined): Array[Int] = js.native
}

/**
  *  From js ADAL library. For details on using ADAL in general: https://msdn.microsoft.com/en-us/library/gg327838.aspx.
  */
@js.native
@JSImport("adal-node", "AuthenticationContext")
class AuthenticationContext(authority: String, validateAuthority: Boolean = true) extends js.Object {
  def authority: js.Object          = js.native
  def options: js.Object            = js.native
  def options_=(o: js.Object): Unit = js.native
  def cache: js.Object              = js.native
  def acquireTokenWithUsernamePassword(resource: String,
                                       username: String,
                                       password: String,
                                       applicationId: String,
                                       callback: js.Function2[js.Error, // can be null but not undefined
                                                              UndefOr[ErrorResponse | TokenInfo],
                                                              Unit]): Unit = js.native
}

trait WhoAmI extends js.Object {
  val BusinessUnitId: String
  val OrganizationId: String
  val UserId: String
}

@js.native
trait ErrorResponse extends js.Object {
  val error: String            = js.native
  val errorDescription: String = js.native
}

case class TokenRequestError(message: String) extends Exception(message)

@js.native
trait TokenInfo extends js.Object {
  val accessToken: String      = js.native
  val expiresOn: js.Date       = js.native
  val tokenType: String        = js.native
  val userId: String           = js.native
  val identityProvider: String = js.native
  val expiresIn: Long          = js.native
}

@js.native
@JSGlobal("Object")
object Object2 extends js.Object {

  /** For the given target, add sources to it. */
  def assign(target: js.Any, sources: js.Any*): js.Object = js.native
}

@js.native
@JSImport("glob", JSImport.Namespace)
object glob extends js.Object {
  @JSName("sync")
  def apply(pattern: String, options: js.Dynamic = js.Dynamic.literal()): Array[String] = js.native
}

object ComponentType {
  val TypesIntToString = Map[Int, String](
    (1, "Entity"),
    (2, "Attribute"),
    (3, "Relationship"),
    (4, "Attribute Picklist Value"),
    (5, "Attribute Lookup Value"),
    (6, "View Attribute"),
    (7, "Localized Label"),
    (8, "Relationship Extra Condition"),
    (9, "Option Set"),
    (10, "Entity Relationship"),
    (11, "Entity Relationship Role"),
    (12, "Entity Relationship Relationships"),
    (13, "Managed Property"),
    (20, "Role"),
    (21, "Role Privilege"),
    (22, "Display String"),
    (23, "Display String Map"),
    (24, "Form"),
    (25, "Organization"),
    (26, "Saved Query"),
    (29, "Workflow"),
    (31, "Report"),
    (32, "Report Entity"),
    (33, "Report Category"),
    (34, "Report Visibility"),
    (35, "Attachment"),
    (36, "Email Template"),
    (37, "Contract Template"),
    (38, "KB Article Template"),
    (39, "Mail Merge Template"),
    (44, "Duplicate Rule"),
    (45, "Duplicate Rule Condition"),
    (46, "Entity Map"),
    (47, "Attribute Map"),
    (48, "Ribbon Command"),
    (49, "Ribbon Context Group"),
    (50, "Ribbon Customization"),
    (52, "Ribbon Rule"),
    (53, "Ribbon Tab To Command Map"),
    (55, "Ribbon Diff"),
    (59, "Saved Query Visualization"),
    (60, "System Form"),
    (61, "Web Resource"),
    (62, "Site Map"),
    (63, "Connection Role"),
    (65, "Hierarchy Rule"),
    (70, "Field Security Profile"),
    (71, "Field Permission"),
    (90, "Plugin Type"),
    (91, "Plugin Assembly"),
    (92, "SDK Message Processing Step"),
    (93, "SDK Message Processing Step Image"),
    (95, "Service Endpoint"),
    (150, "Routing Rule"),
    (151, "Routing Rule Item"),
    (152, "SLA"),
    (153, "SLA Item"),
    (154, "Convert Rule"),
    (155, "Convert Rule Item")
  )

  val TypesStringToInt = TypesIntToString.map(_.swap)
}

class ImportJson(
    val name: String,
    val modecode: Int = ModeCode.Create,
    val sendnotification: js.UndefOr[Boolean] = js.undefined,
    val emailaddress: js.UndefOr[String] = js.undefined,
    val statuscode: js.UndefOr[Int] = js.undefined,
    val createdon: js.UndefOr[String] = js.undefined,
    val statecode: js.UndefOr[Int] = js.undefined,
    val Import_ImportFile: js.UndefOr[js.Array[ImportFileJson]] = js.undefined,
    val Import_AsyncOperations: js.UndefOr[js.Array[AsyncOperationOData]] = js.undefined,
    val importid: js.UndefOr[String] = js.undefined
) extends js.Object

object IsImport {
  val Migration = 0
  val Import    = 1
}
object ModeCode {
  val Create = 0
  val Update = 1
}

class EntityReferenceJson(
    val entitylogicalname: String,
    val id: String
) extends js.Object

class TestJS(
    val name: js.UndefOr[String] = js.undefined
) extends js.Object

class ImportFileJson(
    val name: js.UndefOr[String] = js.undefined,
    val content: js.UndefOr[String] = js.undefined,
    val filetypecode: js.UndefOr[Int] = FileType.csv,
    val isfirstrowheader: js.UndefOr[Boolean] = true,
    val processcode: js.UndefOr[Int] = js.undefined,
    val fielddelimitercode: js.UndefOr[Int] = js.undefined,
    val datadelimitercode: js.UndefOr[Int] = js.undefined,
    val processingstatus: js.UndefOr[Int] = js.undefined,
    val progresscounter: js.UndefOr[Int] = js.undefined,
    val source: js.UndefOr[String] = js.undefined,
    val size: js.UndefOr[Int] = js.undefined,
    val sourceentityname: js.UndefOr[String] = js.undefined,
    val targetentityname: js.UndefOr[String] = js.undefined,
    val successcount: js.UndefOr[Int] = js.undefined,
    val totalcount: js.UndefOr[Int] = js.undefined,
    val failurecount: js.UndefOr[Int] = js.undefined,
    val statuscode: js.UndefOr[Int] = js.undefined,
    val partialfailurecode: js.UndefOr[Int] = js.undefined,
    val usesystemmap: js.UndefOr[Boolean] = false,
    val parsedtablename: js.UndefOr[String] = js.undefined,
    val parsedcolumnsnumber: js.UndefOr[Int] = js.undefined,
    val parsedcolumnprefix: js.UndefOr[String] = js.undefined,
    val headerrow: js.UndefOr[String] = js.undefined,
    val enableduplicatedetection: js.UndefOr[Boolean] = js.undefined,
    val additionalheaderrow: js.UndefOr[String] = js.undefined,
    @JSName("recordsownerid_systemuser@odata.bind") val recordsownerid_systemuser: js.UndefOr[String] = js.undefined,
    @JSName("recordsownerid_team@odata.bind") val recordsownerid_team: js.UndefOr[String] = js.undefined,
    @JSName("importmapid@odata.bind") val importmapid: js.UndefOr[String] = js.undefined,
    @JSName("importid@odata.bind") val importid: js.UndefOr[String] = js.undefined,
    val createdon: js.UndefOr[String] = js.undefined,
    val importfileid: js.UndefOr[String] = js.undefined
) extends js.Object

object DataDelimiter {
  val doublequote = 1
  val none        = 2
  val singlequote = 3
}

object ProcessCode {
  val Process  = 1
  val Ignore   = 2
  val Internal = 3
}

object FileType {
  val csv                = 0
  val xmlspreadsheet2003 = 1
  val attachment         = 2
  val xlsx               = 3

  val extToInt = Map[String, Int]("csv" -> 0, "xmlspreadsheet2003" -> 1, "attachment" -> 2, "xlsx" -> 3)
}

object FieldDelimiter {
  val colon     = 1
  val comma     = 2
  val tab       = 3
  val semicolon = 4
}

class ImportSolution(
    var ImportJobId: js.UndefOr[String] = js.undefined,
    var CustomizationFile: js.UndefOr[String] = js.undefined,
    var OverwriteUnmanagedCustomizations: js.UndefOr[Boolean] = false,
    var PublishWorkflows: js.UndefOr[Boolean] = true,
    var ConvertToManaged: js.UndefOr[Boolean] = false,
    var SkipProductUpdateDependencies: js.UndefOr[Boolean] = true,
    var HoldingSolution: js.UndefOr[Boolean] = false
) extends js.Object

trait PluginAssembly extends js.Object {
  val pluginassemblyid: js.UndefOr[String]       = js.undefined
  val pluginassemblyidunique: js.UndefOr[String] = js.undefined
  val name: js.UndefOr[String]                   = js.undefined

  val _organizationid_value: js.UndefOr[String] = js.undefined
  val isolationmode: js.UndefOr[Int]            = js.undefined
  @JSName("isolationmode_f@OData.Community.Display.V1.FormattedValue")
  val isolationmode_f: js.UndefOr[String]   = js.undefined
  val solutionid: js.UndefOr[String]        = js.undefined
  val publickeytoken: js.UndefOr[String]    = js.undefined
  val customizationlevel: js.UndefOr[Int]   = js.undefined
  val version: js.UndefOr[String]           = js.undefined
  val major: js.UndefOr[Int]                = js.undefined
  val minor: js.UndefOr[Int]                = js.undefined
  val path: js.UndefOr[String]              = js.undefined
  val description: js.UndefOr[String]       = js.undefined
  val content: js.UndefOr[String]           = js.undefined
  val introducedversion: js.UndefOr[String] = js.undefined
  val componentstate: js.UndefOr[Int]       = js.undefined
  @JSName("componentstate@OData.Community.Display.V1.FormattedValue")
  val componentstate_f: js.UndefOr[String] = js.undefined
  val sourcetype: js.UndefOr[Int]          = js.undefined
  @JSName("sourcetype@OData.Community.Display.V1.FormattedValue")
  val sourcetype_f: js.UndefOr[Int] = js.undefined

  val modifiedon: js.UndefOr[String] = js.undefined
  @JSName("modifiedon@OData.Community.Display.V1.FormattedValue")
  val modifiedon_f: js.UndefOr[String] = js.undefined
  val createdon: js.UndefOr[String]    = js.undefined
  @JSName("createdon@OData.Community.Display.V1.FormattedValue")
  val createdon_f: js.UndefOr[String]   = js.undefined
  val overwritetime: js.UndefOr[String] = js.undefined
  @JSName("overwritetime@OData.Community.Display.V1.FormattedValue")
  val overwritetime_f: js.UndefOr[String] = js.undefined

  /** Only available if nav property expanded and then Dynamics only provides a link.... */
  val pluginassembly_plugintype: js.UndefOr[Array[PluginType]] = js.undefined
  @JSName("pluginassembly_plugintype@odata.nextLink@OData.Community.Display.V1.FormattedValue")
  val pluginassembly_plugintype_nextLink: js.UndefOr[String] = js.undefined
}

@js.native
trait PluginType extends js.Object {
  val description: js.UndefOr[String]         = js.undefined
  val solutionid: js.UndefOr[String]          = js.undefined
  val friendlyname: js.UndefOr[String]        = js.undefined
  val major: js.UndefOr[Int]                  = js.undefined
  val minor: js.UndefOr[Int]                  = js.undefined
  val customizationlevel: js.UndefOr[Int]     = js.undefined
  val plugintypeidunique: js.UndefOr[Int]     = js.undefined
  val isworkflowactivity: js.UndefOr[Boolean] = js.undefined
  val componentstate: js.UndefOr[Int]         = js.undefined
  @JSName("componentstate@OData.Community.Display.V1.FormattedValue")
  val componentstate_f: js.UndefOr[String]             = js.undefined
  val assemblyname: js.UndefOr[String]                 = js.undefined
  val _plugin_assemblyid_value: js.UndefOr[String]     = js.undefined
  val version: js.UndefOr[String]                      = js.undefined
  val typename: js.UndefOr[String]                     = js.undefined
  val workflowactivitygroupname: js.UndefOr[String]    = js.undefined
  val customerworkflowactivityinfo: js.UndefOr[String] = js.undefined

  val modifiedon: js.UndefOr[String] = js.undefined
  @JSName("modifiedon@OData.Community.Display.V1.FormattedValue")
  val modifiedon_f: js.UndefOr[String] = js.undefined
  val createdon: js.UndefOr[String]    = js.undefined
  @JSName("createdon@OData.Community.Display.V1.FormattedValue")
  val createdon_f: js.UndefOr[String]   = js.undefined
  val overwritetime: js.UndefOr[String] = js.undefined
  @JSName("overwritetime@OData.Community.Display.V1.FormattedValue")
  val overwritetime_f: js.UndefOr[String] = js.undefined
}

class MSSQLConfig(
    val user: js.UndefOr[String] = js.undefined,
    val password: js.UndefOr[String] = js.undefined,
    val server: js.UndefOr[String] = js.undefined,
    val database: js.UndefOr[String] = js.undefined,
    val port: js.UndefOr[Int] = js.undefined,
    val domain: js.UndefOr[String] = js.undefined,
    val connectionTimeout: js.UndefOr[Int] = js.undefined,
    val requestTimeout: js.UndefOr[Int] = js.undefined,
    val parseJSON: js.UndefOr[Boolean] = js.undefined,
    val stream: js.UndefOr[Boolean] = js.undefined,
    val pool: js.UndefOr[PoolOptions] = js.undefined,
    val options: js.UndefOr[RawOptions] = js.undefined
) extends js.Object

class PoolOptions(
    val max: js.UndefOr[Int] = js.undefined,
    val min: js.UndefOr[Int] = js.undefined,
    val idleTimeoutMillis: js.UndefOr[Int] = js.undefined,
    val acquireTimeoutMillis: js.UndefOr[Int] = js.undefined,
    val fifo: js.UndefOr[Boolean] = js.undefined,
    val priorityRange: js.UndefOr[Int] = js.undefined,
    val autostart: js.UndefOr[Boolean] = js.undefined
    // ...more should go here...
) extends js.Object

@js.native
@JSImport("mssql", JSImport.Namespace)
object MSSQL extends js.Object with IEventEmitter {
  def connect(config: RawOptions | String): js.Promise[ConnectionPool]              = js.native
  def Request(): Request                                                            = js.native
  def ConnectionPool(options: js.UndefOr[PoolOptions | RawOptions]): ConnectionPool = js.native
}

@js.native
trait ConnectionPool extends js.Object with IEventEmitter {
  def request(): Request                    = js.native
  def close(): js.Promise[Unit]             = js.native
  def connect(): js.Promise[ConnectionPool] = js.native
  val connected: Boolean                    = js.native
  val connecting: Boolean                   = js.native
  val driver: String                        = js.native
}

@js.native
trait Result[A] extends js.Object {
  val recordsets: js.Array[js.Any]
  val recordset: js.Any
  val rowsAffected: js.Array[Int]
  val output: js.Dictionary[js.Any]
}

@js.native
trait Request extends js.Object with IEventEmitter {
  def input(p: String, t: Int, value: js.Any): Result[js.Any] = js.native
  var stream: js.UndefOr[Boolean]                             = js.native
  var cancelled: js.UndefOr[Boolean]                          = js.native
  var verbose: js.UndefOr[Boolean]                            = js.native
  def query[A](q: String): js.Promise[Result[A]]              = js.native
  def cancel(): Unit                                          = js.native
}

/**
  * Uses https://github.com/uhop/stream-json
  */
@js.native
trait Source extends js.Object {
  val input: io.scalajs.nodejs.stream.Writable  = js.native
  val output: io.scalajs.nodejs.stream.Readable = js.native
  val streams: js.Array[js.Any]                 = js.native
}

/**
  * Uses https://github.com/uhop/stream-json
  */
@js.native
@JSImport("stream-json/utils/StreamJsonObjects", JSImport.Namespace)
object StreamJsonObjects extends js.Object {
  def make(options: js.UndefOr[RawOptions] = js.undefined): Source = js.native
}

/**
  * Uses https://github.com/uhop/stream-json
  */
@js.native
//@JSImport("stream-json/utils/StreamArray", JSImport.Namespace)
object StreamArray extends js.Object {
  def make(options: js.UndefOr[RawOptions] = js.undefined): Source = js.native
}
