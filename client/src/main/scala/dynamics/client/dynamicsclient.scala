// Copyright (c) 2017 aappddeevv@gmail.com
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package client

import scala.scalajs.js
import js.{|, _}
import scala.concurrent.{Future, ExecutionContext}
import js.annotation._
import fs2._
import fs2.util._
import cats._
import cats.data._
import fs2.interop.cats._
import fs2._
import js.JSConverters._
import cats.syntax.show._

import dynamics.common._
import fs2helpers._
import dynamics.http._

@js.native
trait ErrorOData extends js.Object {
  val code: js.UndefOr[String]                = js.undefined
  val message: js.UndefOr[String]             = js.undefined
  val innererror: js.UndefOr[InnerErrorOData] = js.undefined
}

@js.native
trait InnerErrorOData extends js.Object {
  @JSName("type")
  val etype: js.UndefOr[String]      = js.undefined
  val message: js.UndefOr[String]    = js.undefined
  val stacktrace: js.UndefOr[String] = js.undefined
}

/** Inner error returned by the dynamics server. */
case class InnerError(etype: String, message: String, stacktrace: String)

/** An error specific to Dynamics responding in the body of a message .*/
case class DynamicsServerError(code: String, message: String, innererror: Option[InnerError] = None)

object DynamicsServerError {

  def apply(err: ErrorOData): DynamicsServerError = {
    val ierror = err.innererror.map { i =>
      InnerError(
        i.etype.getOrElse("<no error type provided>"),
        i.message.getOrElse("<no message provided>"),
        i.stacktrace.map(_.replaceAll("\\r\\n", "\n")).getOrElse("<no stacktrace provided>")
      )
    }.toOption

    DynamicsServerError(err.code.filterNot(_.isEmpty).getOrElse("<no code provided>"),
                        err.message.getOrElse("<no message provided>"),
                        ierror)
  }

  implicit val innerErrorShow: Show[InnerError] = Show { e =>
    s"${e.message} (${e.etype})\n${e.stacktrace}"
  }

  implicit val dynamicsServerErrorShow: Show[DynamicsServerError] = Show { e =>
    s"${e.message} (code=${e.code})\n" +
      s"Inner Error: " + e.innererror.map(_.show).getOrElse("<not present>")
  }

}

/** Combines a message, an optional DynamicsServerError, an optional underlying
  * error and the Status of the http call.
  */
sealed abstract class DynamicsError extends RuntimeException {
  def message: String
  final override def getMessage: String = message
  def cause: Option[DynamicsServerError]
  def underlying: Option[Throwable]
  def status: Status

  /** True if there was a server error. */
  def hasServerError = cause.isDefined
}

final case class DynamicsClientError(details: String,
                                     val cause: Option[DynamicsServerError] = None,
                                     underlying: Option[Throwable] = None,
                                     val status: Status)
    extends DynamicsError {
  //cause.foreach(initCause) // which one to use as underlying?
  def message = s"Dynamics client request encountered an error: $details"
}

sealed trait DynamicsId {
  def render(): String
}

case class Id(id: String, name: Option[String] = None) extends DynamicsId {
  def render() = name.map(n => s"$n = $id").getOrElse(id)
}

/** Alternate key. You'll need to quote the value in the string, if that's needed. */
case class AltId(parts: Seq[(String, String)]) extends DynamicsId {
  def render() = parts.map(p => s"${p._1} = ${p._2}").mkString(",")
}

object AltId {
  def apply(e: String, id: String): AltId = AltId(Seq((e, id)))
}

object DynamicsError {

  import Show._
  import DynamicsServerError._

  implicit val showDynamicsError: Show[DynamicsError] = Show { e =>
    s"Dynamics error: ${e.getMessage}\n" +
      s"Status code: ${e.status.show}\n" +
      s"Dynamics server error: " + e.cause.map(_.show).getOrElse("<dynamics server error not provided>") + "\n" +
      s"Underlying error: " + e.underlying.map(_.toString).getOrElse("<underlying error not provided>") + "\n"
  }
}

case class DynamicsOptions(
                           /** Prefer OData options. */
                           prefers: OData.PreferOptions = OData.DefaultPreferOptions,
                           /** Some operations take a version tag. */
                           version: Option[String] = None,
                           /** User GUID for MSCRMCallerId */
                           user: Option[String] = None)

/**
  * Dynamics specific client. Its a thin layer over a basic HTTP client that
  * formulates the HTTP request and minimually interprets the response.
  *
  * All of the methods either return a Task or a Steam. The Task or Stream
  * must be run in order to execute the operation.
  */
case class DynamicsClient(http: Client, private val connectInfo: ConnectionInfo, debug: Boolean = false)
    extends LazyLogger
    with DynamicsHttpClientRequests {

  // resolved when instantiated...
  protected implicit val e: ExecutionContext = implicitly[ExecutionContext]
  protected implicit val s: Strategy         = implicitly[Strategy]

  /** Create a failed task and try to pull out a dynamics server error message from the body.
    */
  protected def responseToFailedTask[A](resp: HttpResponse, msg: String, req: Option[HttpRequest]): Task[A] = {
    resp.body.flatMap { body =>
      logger.debug(s"ERROR: ${resp.status}: RESPONSE BODY: $body")
      val statuserror                       = Option(UnexpectedStatus(resp.status, request = req, response = Option(resp)))
      val json                              = JSON.parse(body)
      val dynamicserror: Option[ErrorOData] = findDynamicsError(json)
      val derror =
        dynamicserror.map(e => DynamicsClientError(msg, Some(DynamicsServerError(e)), statuserror, resp.status))
      val simpleerror = findSimpleMessage(json).map(DynamicsClientError(_, None, statuserror, resp.status))
      val fallback    = Option(DynamicsClientError(msg, None, statuserror, resp.status))
      Task.fail((derror orElse simpleerror orElse fallback).get)
    }
  }

  protected def findSimpleMessage(body: js.Dynamic): Option[String] = {
    val error: js.UndefOr[js.Dynamic] = body.Message
    error.map(_.asInstanceOf[String]).toOption
  }

  /** Find an optional dynamics error message in the body. */
  protected def findDynamicsError(body: js.Dynamic): Option[ErrorOData] = {
    val error: js.UndefOr[js.Dynamic] = body.error
    error.map(_.asInstanceOf[ErrorOData]).toOption
  }

  /** Exposed so you can formulate batch request from standard HttpRequest objects which must have
    * HOST header set or the full URL in the batch item request.
    */
  val base =
    if (connectInfo.dataUrl.get.endsWith("/")) connectInfo.dataUrl.get.dropRight(1)
    else connectInfo.dataUrl.get

  //private val reg = """[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}""".r

  /** Update a single property, return the id updated. */
  def updateOneProperty(entitySet: String,
                        id: String,
                        property: String,
                        value: js.Any,
                        opts: DynamicsOptions = DefaultDynamicsOptions): Task[String] = {
    val b = ""
    val request =
      HttpRequest(Method.PUT, s"/$entitySet($id)/$property", body = Entity.fromString(b), headers = toHeaders(opts))
    http.fetch[String](request) {
      case Status.Successful(resp) if (resp.status == Status.NoContent) => Task.now(id)
      case failedResponse                                               => responseToFailedTask(failedResponse, s"Update $entitySet($id)", Option(request))
    }
  }

  /** Run a batch request. */
  def batch[A](r: HttpRequest, m: Multipart)(implicit d: EntityDecoder[A]): Task[A] = {
    import OData._
    r match {
      case HttpRequest(_, _, headers, body) => // this seems useless, never really use r...
        val (mrendered, xtra) = EntityEncoder[Multipart].encode(m)
        val therequest        = HttpRequest(Method.POST, "/$batch", headers = headers ++ xtra, body = mrendered)
        http.fetch[A](therequest) {
          case Status.Successful(resp) => resp.as[A]
          case failedResponse =>
            responseToFailedTask(failedResponse, s"Batch", Option(therequest))
          case _ => // never reaches here??!?!
            Task.fail(new IllegalArgumentException("Batch request requires a Multipart body."))
        }
    }
  }

  /**
    * Update an entity. Fails if no odata-entityid is returned in the header.
    * For now, do not use return=representation.
    *
    */
  def update(entitySet: String,
             id: String,
             body: String,
             upsertPreventCreate: Boolean = false,
             upsertPreventUpdate: Boolean = false,
             opts: DynamicsOptions = DefaultDynamicsOptions): Task[String] = {
    val request = mkUpdateRequest(entitySet, id, body, upsertPreventCreate, upsertPreventUpdate, opts)
    //HttpRequest(Method.PATCH, s"/$entitySet($id)", body = Entity.fromString(body), headers = toHeaders(opts) ++ h )
    http.fetch[String](request) {
      case Status.Successful(resp) => Task.now(id)
      case failedResponse =>
        responseToFailedTask(failedResponse, s"Update $entitySet($id)", Option(request))
    }
  }

  /** Create an entity and expect only its id returned. includeRepresentation is set to an explicit
    * false in the headers to ensure the id is returend in the header.
    */
  def createReturnId(entityCollection: String,
                     body: String,
                     opts: DynamicsOptions = DefaultDynamicsOptions): Task[String] = {
    val newOpts = opts.copy(prefers = opts.prefers.copy(includeRepresentation = Some(false)))
    create[String](entityCollection, body, newOpts)(EntityDecoder.ReturnedIdDecoder)
  }

  /** Create an entity. If return=representation then the decoder can decode the body with entity content.
    * You can return an id or body from this function.
    */
  def create[A](entitySet: String, body: String, opts: DynamicsOptions = DefaultDynamicsOptions)(
      implicit d: EntityDecoder[A]): Task[A] = {
    //val request = HttpRequest(Method.POST, s"/$entitySet", body=Entity.fromString(body), headers=toHeaders(opts))
    val request = mkCreateRequest(entitySet, body, opts)
    http.fetch(request) {
      case Status.Successful(resp) => resp.as[A]
      case failedResponse =>
        responseToFailedTask(failedResponse, s"Create for $entitySet", Option(request))
    }
  }

  /** Create an entity. Return the id passed in for convenience. Return true if
    * the entity does not exist even though this call did not technically delete it.
    * @param entityCollection Entity
    * @param keyInfo Primary key (GUID) or alternate key.
    * @return Pair of the id passed in and true if deleted (204), false if not (404).
    */
  def delete(entitySet: String,
             keyInfo: DynamicsId,
             opts: DynamicsOptions = DefaultDynamicsOptions): Task[(DynamicsId, Boolean)] = {
    // Status 204 indicates success, status 404 indicates the entity did not exist.
    val request = mkDeleteRequest(entitySet, keyInfo, opts)
    http.fetch(request) {
      case Status.Successful(resp)                               => Task.now((keyInfo, true))
      case Status.ClientError(resp) if (resp.status.code == 404) => Task.now((keyInfo, true))
      case failedResponse =>
        responseToFailedTask(failedResponse, s"Delete for $entitySet($keyInfo)", Option(request))
    }
  }

  /**
    * Execute an action. Can be bound or unbound depending on entityCollectionAndId
    *
    * TODO: Map into a http.expect.
    */
  def executeAction[A](action: String,
                       body: Entity,
                       entitySetAndId: Option[(String, String)] = None,
                       opts: DynamicsOptions = DefaultDynamicsOptions)(implicit d: EntityDecoder[A]): Task[A] = {
    val request = mkExecuteActionRequest(action, body, entitySetAndId, opts)
    http.fetch(request) {
      case Status.Successful(resp) => resp.as[A]
      case failedResponse =>
        responseToFailedTask(failedResponse, s"Executing action $action, $body, $entitySetAndId", Option(request))
    }
  }

  /** Exceute a bound or unbound function.
    *
    * @param function Function name
    * @param parameters Query parameters for function.
    * @param entity Optional (entityCollection, id) info for bound function call. Use None for unbound function call.
    * @param d Decode response body.
    * @return
    */
  def executeFunction[A](function: String,
                         parameters: Map[String, scala.Any] = Map.empty,
                         entity: Option[(String, String)] = None)(implicit d: EntityDecoder[A]): Task[A] = {
    val req = mkExecuteFunctionRequest(function, parameters, entity)
    http.expect(req)(d)
  }

  /** Associate an existing entity to another through a navigation property. */
  def associate(fromEntitySet: String,
                fromEntityId: String,
                navProperty: String,
                toEntitySet: String,
                toEntityId: String): Task[Boolean] = {
    val request = mkAssociateRequest(fromEntitySet, fromEntityId, navProperty, toEntitySet, toEntityId)
    http.fetch(request) {
      case Status.Successful(resp) => Task.now(true)
      case failedResponse =>
        responseToFailedTask(failedResponse,
                             s"Association $fromEntitySet($fromEntityId)->$navProperty->$toEntitySet($toEntityId)",
                             Option(request))
    }
  }

  /** Disassociate an entity fram a navigation property. */
  def disassociate(fromEntitySet: String,
                   fromEntityId: String,
                   navProperty: String,
                   to: Option[(String, String)]): Task[Boolean] = {
    val request = mkDisassocatiateRequest(fromEntitySet, fromEntityId, navProperty, to)
    http.fetch(request) {
      case Status.Successful(resp) => Task.now(true)
      case failedResponse =>
        responseToFailedTask(failedResponse,
                             s"Disassociation $fromEntitySet($fromEntityId)->$navProperty->$to",
                             Option(request))
    }
  }

  /**
    * Get a single entity using key information. The keyInfo can be a guid or alternate key criteria e.g "altkeyattribute='Larry',...".
    * You get all the fields with this.
    *
    * Allow you to specify a queryspec somehow as well.
    */
  def getOneWithKey[A](entitySet: String, keyInfo: DynamicsId, opts: DynamicsOptions = DefaultDynamicsOptions)(
      implicit d: EntityDecoder[A]): Task[A] =
    getOne(s"/$entitySet(${keyInfo.render()})", opts)(d)

  /** Get one entity using a full query url. If you use a URL that
    * returns a OData response with a `value` array that will be automatically
    *  extract, you need to use a EntityDecoder that first looks for that
    *  array then obtains your `A`. See `EntityDecoder.ValueWrapper` for an example.
    *  You often use this function when you want to use a single entity and a 1:M
    *  navigation property to retrieve your "list" of entities e.g. a single entity's
    *  set of connections or some child entity. In this case, your URL will typically
    *  have an "expand" segment.
    */
  def getOne[A](url: String, opts: DynamicsOptions = DefaultDynamicsOptions)(implicit d: EntityDecoder[A]): Task[A] = {
    val request = HttpRequest(Method.GET, url, headers = toHeaders(opts))
    http.fetch(request) {
      case Status.Successful(resp) => resp.as[A]
      case failedResponse =>
        responseToFailedTask(failedResponse, s"Get one entity $url", Option(request))
    }
  }

  /**
    * Get a list of values. Follows @data.nextLink but accumulates
    * all the results into memory. Prefer [[getListStream]]. For now,
    * the caller must decode external to this method.
    */
  def getList[A <: js.Any](url: String, opts: DynamicsOptions = DefaultDynamicsOptions)(): Task[Seq[A]] =
    getListStream[A](url).runLog

  /**
    * Get a list of values as a stream. Follows @odata.nextLink. For now, the caller
    * must decode external to this method.
    */
  def getListStream[A <: js.Any](url: String, opts: DynamicsOptions = DefaultDynamicsOptions): Stream[Task, A] = {
    val str: Stream[Task, Seq[A]] = Stream.unfoldEval(Option(url)) {
      _ match {
        // Return a Task[Option[(Seq[A],Option[String])]]
        case None => Task.now(None)
        case Some(nextLink) =>
          val request = HttpRequest(Method.GET, nextLink, headers = toHeaders(opts))
          http.fetch(request) {
            case Status.Successful(resp) =>
              resp.body.map { str =>
                val odata = JSON.parse(str).asInstanceOf[ValueArrayResponse[A]]
                logger.debug(s"getListStream: body=$str\nodata=${PrettyJson.render(odata)}")
                val a = odata.value.map(_.toSeq) getOrElse Seq()
                //println(s"getList: a=$a,\n${PrettyJson.render(a(0).asInstanceOf[js.Object])}")
                Option((a, odata.nextLink.toOption))
              }
            case failedResponse =>
              responseToFailedTask(failedResponse, s"getListStream $url", Option(request))
          }
      }
    }
    // Flatten the seq chunks from each unfold iteration
    str.flatMap(Stream.emits)
  }

}

trait DynamicsHttpClientRequests {

  /** Base URL to help make requests when needed. */
  def base: String

  val DefaultBatchRequest = HttpRequest(Method.PUT, "/$batch")

  def mkGetListRequest(url: String, opts: DynamicsOptions = DefaultDynamicsOptions) =
    HttpRequest(Method.GET, url, headers = toHeaders(opts))

  def mkCreateRequest(entitySet: String, body: String, opts: DynamicsOptions = DefaultDynamicsOptions) =
    HttpRequest(Method.POST, s"/$entitySet", body = Entity.fromString(body), headers = toHeaders(opts))

  /** Make a pure delete request. */
  def mkDeleteRequest(entitySet: String, keyInfo: DynamicsId, opts: DynamicsOptions = DefaultDynamicsOptions) =
    HttpRequest(Method.DELETE, s"/$entitySet(${keyInfo.render()})", headers = toHeaders(opts))

  def mkGetOneRequest(url: String, opts: DynamicsOptions) =
    HttpRequest(Method.GET, url, headers = toHeaders(opts))

  def mkExecuteActionRequest(action: String,
                             body: Entity,
                             entitySetAndId: Option[(String, String)] = None,
                             opts: DynamicsOptions = DefaultDynamicsOptions) = {
    val url = entitySetAndId.map { case (c, i) => s"/$c($i)/$action" }.getOrElse(s"/$action")
    HttpRequest(Method.POST, url, body = body, headers = toHeaders(opts))
  }

  def toHeaders(o: DynamicsOptions): HttpHeaders = {
    val prefer = OData.render(o.prefers)
    prefer.map(str => HttpHeaders("Prefer"        -> str)).getOrElse(HttpHeaders.empty) ++
      o.user.map(u => HttpHeaders("MSCRMCallerId" -> u)).getOrElse(HttpHeaders.empty)
  }

  /** Not sure adding $base to the @odata.id is correct. */
  def mkAssociateRequest(fromEntitySet: String,
                         fromEntityId: String,
                         navProperty: String,
                         toEntitySet: String,
                         toEntityId: String): HttpRequest = {
    val url  = s"/${fromEntitySet}(${fromEntityId})/$navProperty/$$ref"
    val body = s"""'data' : {'@odata.id': '$base/$toEntitySet($toEntityId)'}"""
    HttpRequest(Method.PUT, url, body = Entity.fromString(body))
  }

  def mkDisassocatiateRequest(fromEntitySet: String,
                              fromEntityId: String,
                              navProperty: String,
                              to: Option[(String, String)]): HttpRequest = {
    val url = s"/$fromEntitySet($fromEntityId)/$navProperty/$$ref" +
      to.map { case (eset, id) => s"?$$id=$base/$eset($id)" }.getOrElse("")
    HttpRequest(Method.DELETE, url, body = Entity.empty)
  }

  def mkUpdateRequest[A](
      entitySet: String,
      id: String,
      body: A,
      upsertPreventCreate: Boolean = false,
      upsertPreventUpdate: Boolean = false,
      opts: DynamicsOptions = DefaultDynamicsOptions)(implicit enc: EntityEncoder[A]): HttpRequest = {
    val (b, xtra) = enc.encode(body)
    val h: HttpHeaders =
      if (upsertPreventCreate) HttpHeaders("If-Match" -> "*")
      else if (upsertPreventUpdate) HttpHeaders("If-None-Match" -> "*")
      else HttpHeaders.empty
    val mustHave = HttpHeaders.empty ++ Map("Content-Type" -> Seq("application/json", "type=entry"))
    HttpRequest(Method.PATCH, s"$base/$entitySet($id)", toHeaders(opts) ++ h ++ xtra ++ mustHave, b)
  }

  def mkExecuteFunctionRequest(function: String,
                               parameters: Map[String, scala.Any] = Map.empty,
                               entity: Option[(String, String)] = None) = {
    // (parm, parmvalue)
    val q: Seq[(String, String)] = parameters.keys.zipWithIndex
      .map(x => (x._1, x._2 + 1))
      . // start from 1
      map {
        case (k, i) =>
          parameters(k) match {
            case s: String => (s"$k=@p$i", s"@p$i='$s'")
            case x @ _     => (s"$k=@p$i", s"@p$i=$x")
          }
      }
      .toSeq

    val pvars        = q.map(_._1).mkString(",")
    val pvals        = (if (q.size > 0) "?" else "") + q.map(_._2).mkString("&")
    val functionPart = s"/$function($pvars)$pvals"

    val entityPart = entity.map(p => s"${p._1}(${p._2})").getOrElse("")

    val url = s"/$entityPart$functionPart"
    HttpRequest(Method.GET, url)
  }

}
