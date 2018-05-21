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

@js.native
@JSImport("prettyjson", JSImport.Namespace)
object PrettyJson extends js.Object {
  def render(data: js.Object, options: js.UndefOr[PrettyJsonOptions] = js.undefined): String = js.native
}

class PrettyJsonOptions(
    val noColor: js.UndefOr[Boolean] = js.undefined
) extends js.Object

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
trait ImportLogJS extends js.Object {
  val name: String                          = js.native
  val additionalinfo: js.UndefOr[String]    = js.native
  val columnvalue: js.UndefOr[String]       = js.native
  val createdon: js.UndefOr[js.Date]        = js.native // translated automatically in http layer
  val errordescription: js.UndefOr[String]  = js.native
  val errornumber: js.UndefOr[Int]          = js.native
  val headercolumn: js.UndefOr[String]      = js.native
  val importlogid: js.UndefOr[String]       = js.native
  val importlogidunique: js.UndefOr[String] = js.native
  val linenumber: js.UndefOr[Int]           = js.native
  val logphasecode: js.UndefOr[Int]         = js.native
  @JSName("logphasecode@OData.Community.Display.V1.FormattedValue")
  val logphasecode_fv: js.UndefOr[Int] = js.native
  val sequencenumber: js.UndefOr[Int]  = js.native
  val solutionid: js.UndefOr[String]   = js.native
}

/** A single line of data imported from a file. */
@js.native
trait ImportDataJS extends js.Object {
  val data: js.UndefOr[String]         = js.native
  val errortype: js.UndefOr[Int]       = js.native
  val haserror: js.UndefOr[Boolean]    = js.native
  val importdataid: js.UndefOr[String] = js.native
  val linenumber: js.UndefOr[Int]      = js.native
  val recordid: js.UndefOr[String]     = js.native
  val solutionid: js.UndefOr[String]   = js.native
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
trait StatusCode extends js.Object {
  var statecode: UndefOr[Int]           = js.native
  @JSName("statecode@OData.Community.Display.V1.FormattedValue")
  var statecode_fv: UndefOr[String] = js.native

  var statuscode: UndefOr[Int]          = js.native
  @JSName("statuscode@OData.Community.Display.V1.FormattedValue")
  var statuscode_fv: UndefOr[String] = js.native
}

@js.native
trait AsyncOperationOData extends StatusCode {
  val asyncoperationid: UndefOr[String] = js.native
  val name: UndefOr[String]             = js.native
  val startedon: UndefOr[String]        = js.native

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
  val expiresIn: Int           = js.native
}

@js.native
@JSGlobal("Object")
object Object2 extends js.Object {

  /** For the given target, add sources to it. */
  def assign(target: js.Any, sources: js.Any*): js.Object = js.native
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
  val createdon: js.UndefOr[String] = js.undefined,

  val statuscode: js.UndefOr[Int] = js.undefined,
  val statecode: js.UndefOr[Int] = js.undefined,

    val Import_ImportFile: js.UndefOr[js.Array[ImportFileJson]] = js.undefined,
    val Import_AsyncOperations: js.UndefOr[js.Array[AsyncOperationOData]] = js.undefined,
    val importid: js.UndefOr[String] = js.undefined
) extends js.Object

@js.native
trait ImportJSListing extends js.Object {
  val name: String
  val importid: String
  @JSName("statuscode@OData.Community.Display.V1.FormattedValue")
  val statuscode_fv: String
  @JSName("createdon@OData.Community.Display.V1.FormattedValue")
  val createdon_fv: String
  val sequence: Int
}

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

trait ImportFileJson extends js.Object with ImportFileJsonFV {
  var name: js.UndefOr[String]                      = js.undefined
  var content: js.UndefOr[String]                   = js.undefined
  var filetypecode: js.UndefOr[Int]                 = js.undefined
  var isfirstrowheader: js.UndefOr[Boolean]         = js.undefined
  var processcode: js.UndefOr[Int]                  = js.undefined
  var fielddelimitercode: js.UndefOr[Int]           = js.undefined
  var datadelimitercode: js.UndefOr[Int]            = js.undefined
  var processingstatus: js.UndefOr[Int]             = js.undefined
  var progresscounter: js.UndefOr[Int]              = js.undefined
  var source: js.UndefOr[String]                    = js.undefined
  var size: js.UndefOr[Int]                         = js.undefined
  var sourceentityname: js.UndefOr[String]          = js.undefined
  var targetentityname: js.UndefOr[String]          = js.undefined
  var successcount: js.UndefOr[Int]                 = js.undefined
  var totalcount: js.UndefOr[Int]                   = js.undefined
  var failurecount: js.UndefOr[Int]                 = js.undefined
  var statuscode: js.UndefOr[Int]                   = js.undefined
  var partialfailurecount: js.UndefOr[Int]          = js.undefined
  var usesystemmap: js.UndefOr[Boolean]             = js.undefined
  var parsedtablename: js.UndefOr[String]           = js.undefined
  var parsedcolumnsnumber: js.UndefOr[Int]          = js.undefined
  var parsedcolumnprefix: js.UndefOr[String]        = js.undefined
  var headerrow: js.UndefOr[String]                 = js.undefined
  var enableduplicatedetection: js.UndefOr[Boolean] = js.undefined
  var additionalheaderrow: js.UndefOr[String]       = js.undefined
  var createdon: js.UndefOr[String]                 = js.undefined
  var importfileid: js.UndefOr[String]              = js.undefined
}

trait ImportFileJsonFV extends js.Object {
  @JSName("recordsownerid_systemuser@odata.bind")
  var recordsownerid_systemuser: js.UndefOr[String] = js.undefined
  @JSName("recordsownerid_team@odata.bind")
  var recordsownerid_team: js.UndefOr[String] = js.undefined
  @JSName("importmapid@odata.bind")
  var importmapid: js.UndefOr[String] = js.undefined
  @JSName("importid@odata.bind")
  var importid: js.UndefOr[String] = js.undefined

  @JSName("statuscode@OData.Community.Display.V1.FormattedValue")
  var statuscode_fv: js.UndefOr[String] = js.undefined

  @JSName("processingstatus@OData.Community.Display.V1.FormattedValue")
  var processingstatus_fv: js.UndefOr[String] = js.undefined

  @JSName("ImportLog_ImportFile@odata.nextLink")
  var ImportLog_ImportFile_nl: js.UndefOr[String] = js.undefined
}

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

trait AppModule extends js.Object {
  var appmoduleid: js.UndefOr[String]         = js.undefined
  var appmoduleidunique: js.UndefOr[String]   = js.undefined
  var appmoduleversion: js.UndefOr[String]    = js.undefined
  var appmodulexmlmanaged: js.UndefOr[String] = js.undefined
  var clienttype: js.UndefOr[Int]             = js.undefined
  var componentstate: js.UndefOr[Int]         = js.undefined
  var configxml: js.UndefOr[String]           = js.undefined
  var description: js.UndefOr[String]         = js.undefined
  var isdefault: js.UndefOr[Boolean]          = js.undefined
  var isfeatured: js.UndefOr[Boolean]         = js.undefined
  var ismanaged: js.UndefOr[Boolean]          = js.undefined
  var name: js.UndefOr[String]                = js.undefined
  var solutionid: js.UndefOr[String]          = js.undefined
  var uniquename: js.UndefOr[String]          = js.undefined
  var url: js.UndefOr[String]                 = js.undefined
  var webresourceid: js.UndefOr[String]       = js.undefined
  var welcomepagedid: js.UndefOr[String]      = js.undefined

  val _organizationid_value: js.UndefOr[String] = js.undefined
  @JSName("_organizationid_value@OData.Community.Display.V1.FormattedValue")
  val _organizationid_value_fv: js.UndefOr[String] = js.undefined
  @JSName("componentstate@OData.Community.Display.V1.FormattedValue")
  val componentstate_fv: js.UndefOr[String] = js.undefined
  @JSName("formfactor@OData.Community.Display.V1.FormattedValue")
  val formfactor_fv: js.UndefOr[String] = js.undefined
  @JSName("isdefault@OData.Community.Display.V1.FormattedValue")
  val isdefault_fv: js.UndefOr[String] = js.undefined
  @JSName("isfeature@OData.Community.Display.V1.FormattedValue")
  val isfeature_fv: js.UndefOr[String] = js.undefined
  @JSName("ismanaged@OData.Community.Display.V1.FormattedValue")
  val ismanaged_fv: js.UndefOr[String] = js.undefined
}

@js.native
trait EntityRecordCountCollection extends js.Object {
  val Count: Int
  val IsReadOnly: Boolean
  val Keys: js.Array[String]
  val Values: js.Array[Int]
}

@js.native
trait RetrieveTotalRecordCountResponse extends js.Object {
  val EntityRecordCountCollection: EntityRecordCountCollection
}

@js.native
trait SystemuserJS extends js.Object {
  val systemuserid: String                       = js.native
  val internalemailaddress: String               = js.native
  val lastname: String                           = js.native
  val firstname: String                          = js.native
  val fullname: String                           = js.native
  val isdisabled: Boolean                        = js.native
  val isemailaddressapprovedbyo365admin: Boolean = js.native
  val isintegrationuser: Boolean                 = js.native
  val islicensed: Boolean                        = js.native
  val nickname: String                           = js.native
  val jobtitle: String                           = js.native
  val personalemailaddress: String               = js.native
  val photourl: String                           = js.native
  val setupuser: Boolean                         = js.native
  val sharepointemailaddress: Boolean            = js.native
  val userlicensetype: Int                       = js.native

  // ...
}

/*
@js.native
trait SystemuserJS extends js.Object {
  val firstname: String            = js.native
  val lastname: String             = js.native
  val fullname: String             = js.native
  val systemuserid: String         = js.native
  val internalemailaddress: String = js.native

  //@JSName("systemuserroles_association@odata.nextLink")
  //val systemuserroles_association_nl: js.UndefOr[String] = js.native
}
 */

@js.native
trait TeamJS extends js.Object {
  val teamid: String         = js.native
  val ownerid: String        = js.native
  val teamtype: Int          = js.native
  val name: String           = js.native
  val description: String    = js.native
  val organizationid: String = js.native

  /**
    * Only present when using expand=teammembership_association on team fetch.
    * Or use getList with a property path to a "collection value property"
    * and the list will in "value".
    */
  val teammembership_association: js.UndefOr[js.Array[SystemuserJS]] = js.native
}

@js.native
trait RoleNameJS extends js.Object {
  val roleid: String = js.native
  val name: String   = js.native
}

@js.native
trait RoleJS extends RoleNameJS {
  // pure GUID
  val organizationid: String          = js.native
  val _businesunitid_value: String    = js.native
  val solutionid: String              = js.native
  val _parentrootroleid_value: String = js.native

  @JSName("_businessunitid_value@OData.Community.Display.V1.FormattedValue")
  val _businesunitid_value_fv: String = js.native
  @JSName("_parentrootroleid_value@OData.Community.Display.V1.FormattedValue")
  val _parentrootroleid_value_fv: String = js.native
  @JSName("componentstate@OData.Community.Display.V1.FormattedValue")
  val componentstate_fv: String = js.native
}

@js.native
trait Theme extends js.Object {
  val themeid: String
  val name: String
  val `type`: Boolean
  val isdefaulttheme: Boolean
  val _logoimage_value: js.UndefOr[String] = js.undefined
}

@js.native
trait BusinessUnitJS extends js.Object {
  val businessunitid: String
  val name: String
}
