// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package client

import scala.scalajs.js
import scala.concurrent.{Future, ExecutionContext}
import js.annotation._
import fs2._
import cats._
import cats.data._
import cats.effect._
import fs2._
import js.JSConverters._

import dynamics.common._
import fs2helpers._
import dynamics.http._
import dynamics.http.instances.entityEncoder._
import dynamics.http.instances.entityDecoder._

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

case class DynamicsOptions(
    /** Prefer OData options. */
    prefers: OData.PreferOptions = OData.DefaultPreferOptions,
    /** Some operations take a version tag ("etag"). */
    version: Option[String] = None,
    /** User GUID for MSCRMCallerId */
    user: Option[String] = None,
    /**
      * Controls the use of `version` in some request scenarios.
      * DELETE + version => If-Match: version, delete succeeds if etag matches (204), fails otherwise (412).
      * PATCH (update) + version => If-Match: version, update succeeds if etage matches (204), fails otherwise (412)
      */
    applyOptimisticConcurrency: Option[Boolean] = None
)

/**
  * Dynamics specific client. Its a thin layer over a basic HTTP client that
  * formulates the HTTP request and minimually interprets the response.
  *
  * All of the methods either return a IO or a Steam. The IO or Stream must be
  * run in order to execute the operation.
  *
  * You must make sure you have a `MonadError[IO, Throwable]` in implicit scope
  * when you create the client.  If you need to you can always `val ehandler =
  * MonadError[IO, Throwable]` when creating the client to instill your own
  * strategy for raising an error. If you have cats.effect in scope, you should
  * pick up the default one automatically. All errors from responses are
  * captured in this layer and converted to instances of DynamicsClientError (a
  * Throwable). Errors generated from lower levels can be thrown and are *not*
 * captured in this layer.
 * 
  */
case class DynamicsClient(http: Client, private val connectInfo: ConnectionInfo, debug: Boolean = false)(
    implicit ehandler: ApplicativeError[IO, Throwable],
    e: ExecutionContext)
    extends LazyLogger
    with DynamicsClientRequests {

  /**
    * Create a failed effect and pull out a dynamics server error message from the body, if present.
    */
  def responseToFailedTask[A](resp: HttpResponse, msg: String, req: Option[HttpRequest]): IO[A] = {
    resp.body.flatMap { body =>
      logger.debug(s"ERROR: ${resp.status}: RESPONSE BODY: $body")
      val statuserror                       = Option(UnexpectedStatus(resp.status, request = req, response = Option(resp)))
      val json                              = js.JSON.parse(body)
      val dynamicserror: Option[ErrorOData] = findDynamicsError(json)
      val derror =
        dynamicserror.map(e => DynamicsClientError(msg, Some(DynamicsServerError(e)), statuserror, resp.status))
      val simpleerror = findSimpleMessage(json).map(DynamicsClientError(_, None, statuserror, resp.status))
      val fallback    = Option(DynamicsClientError(msg, None, statuserror, resp.status))
      ehandler.raiseError((derror orElse simpleerror orElse fallback).get)
    }
  }

  /** 
   * Not sure when this might apply. Do we get errors where the error property
   * is embedded on a Message (capital?) field?
   */
  protected def findSimpleMessage(body: js.Dynamic): Option[String] = {
    val error: js.UndefOr[js.Dynamic] = body.Message
    error.map(_.asInstanceOf[String]).toOption
  }

  /** Find an optional dynamics error message in the body. */
  protected def findDynamicsError(body: js.Dynamic): Option[ErrorOData] = {
    if(js.DynamicImplicits.truthValue(body.error)) {
      val error: js.UndefOr[js.Dynamic] = body.error
      error.map(_.asInstanceOf[ErrorOData]).toOption
    }
    else None
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
                        opts: DynamicsOptions = DefaultDynamicsOptions): IO[String] = {
    val b = ""
    val request =
      HttpRequest(Method.PUT, s"/$entitySet($id)/$property", body = Entity.fromString(b), headers = toHeaders(opts))
    http.fetch[String](request) {
      case Status.Successful(resp) if (resp.status == Status.NoContent) => IO.pure(id)
      case failedResponse                                               => responseToFailedTask(failedResponse, s"Update $entitySet($id)", Option(request))
    }
  }

  /** Run a batch request. */
  def batch[A](r: HttpRequest, m: Multipart)(implicit d: EntityDecoder[A]): IO[A] = {
    import OData._
    r match {
      case HttpRequest(_, _, headers, body) => // this seems useless, never really use r...
        val (mrendered, xtra) = EntityEncoder[Multipart].encode(m)
        val therequest        = HttpRequest(Method.POST, "/$batch", headers = headers ++ xtra, body = mrendered)
        http.fetch[A](therequest) {
          case Status.Successful(resp) => resp.as[A]
          case failedResponse =>
            responseToFailedTask(failedResponse, s"Batch", Option(therequest))
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
             opts: DynamicsOptions = DefaultDynamicsOptions): IO[String] = {
    val request = mkUpdateRequest(entitySet, id, body, upsertPreventCreate, upsertPreventUpdate, opts, Some(base))
    //HttpRequest(Method.PATCH, s"/$entitySet($id)", body = Entity.fromString(body), headers = toHeaders(opts) ++ h )
    http.fetch[String](request) {
      case Status.Successful(resp) => IO.pure(id)
      case failedResponse =>
        responseToFailedTask(failedResponse, s"Update $entitySet($id)", Option(request))
    }
  }

  /** Create an entity and expect only its id returned. includeRepresentation is set to an explicit
    * false in the headers to ensure the id is returend in the header.
    */
  def createReturnId(entityCollection: String,
                     body: String,
                     opts: DynamicsOptions = DefaultDynamicsOptions): IO[String] = {
    val newOpts = opts.copy(prefers = opts.prefers.copy(includeRepresentation = Some(false)))
    create[String](entityCollection, body, newOpts)(ReturnedIdDecoder)
  }

  /** Create an entity. If return=representation then the decoder can decode the body with entity content.
    * You can return an id or body from this function.
    */
  def create[A](entitySet: String, body: String, opts: DynamicsOptions = DefaultDynamicsOptions)(
      implicit d: EntityDecoder[A]): IO[A] = {
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
             opts: DynamicsOptions = DefaultDynamicsOptions): IO[(DynamicsId, Boolean)] = {
    // Status 204 indicates success, status 404 indicates the entity did not exist.
    val request = mkDeleteRequest(entitySet, keyInfo, opts)
    http.fetch(request) {
      case Status.Successful(resp)                               => IO.pure((keyInfo, true))
      case Status.ClientError(resp) if (resp.status.code == 404) => IO.pure((keyInfo, true))
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
                       opts: DynamicsOptions = DefaultDynamicsOptions)(implicit d: EntityDecoder[A]): IO[A] = {
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
                         entity: Option[(String, String)] = None)(implicit d: EntityDecoder[A]): IO[A] = {
    val req = mkExecuteFunctionRequest(function, parameters, entity)
    http.expect(req)(d)
  }

  /** Associate an existing entity to another through a navigation property. */
  def associate(fromEntitySet: String,
                fromEntityId: String,
                navProperty: String,
                toEntitySet: String,
                toEntityId: String): IO[Boolean] = {
    val request = mkAssociateRequest(fromEntitySet, fromEntityId, navProperty, toEntitySet, toEntityId, base)
    http.fetch(request) {
      case Status.Successful(resp) => IO.pure(true)
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
                   to: Option[(String, String)]): IO[Boolean] = {
    val request = mkDisassocatiateRequest(fromEntitySet, fromEntityId, navProperty, to, base)
    http.fetch(request) {
      case Status.Successful(resp) => IO.pure(true)
      case failedResponse =>
        responseToFailedTask(failedResponse,
                             s"Disassociation $fromEntitySet($fromEntityId)->$navProperty->$to",
                             Option(request))
    }
  }

  /**
    * Get a single entity using key information. The keyInfo can be a guid or
    * alternate key criteria e.g "altkeyattribute='Larry',...".  You get all the
    * fields with this.
    *
    * Allow you to specify a queryspec somehow as well.
    */
  def getOneWithKey[A](entitySet: String, keyInfo: DynamicsId, opts: DynamicsOptions = DefaultDynamicsOptions)(
      implicit d: EntityDecoder[A]): IO[A] =
    getOne(s"/$entitySet(${keyInfo.render()})", opts)(d)

  /**
    * Get one entity using a full query url.
    *
    * If you use a URL that returns a OData response with a `value` array that
    * will be automatically extract, you need to use a EntityDecoder that first
    * looks for that array then obtains your `A`. See
    * `EntityDecoder.ValueWrapper` for an example.  You often use this pattern
    * when employing `getOne` to obtain related records in a 1:M navigation
    * property e.g. a single entity's set of connections or some child
    * entity. In this case, your URL will typically have an "expand" segment.
    */
  def getOne[A](url: String, opts: DynamicsOptions = DefaultDynamicsOptions)(implicit d: EntityDecoder[A]): IO[A] = {
    val request = HttpRequest(Method.GET, url, headers = toHeaders(opts))
    http.fetch(request) {
      case Status.Successful(resp) => resp.as[A]
      case failedResponse =>
        responseToFailedTask(failedResponse, s"Get one entity $url", Option(request))
    }
  }

  /**
    * Get a list of values. Follows @data.nextLink but accumulates all the
    * results into memory. Prefer [[getListStream]]. For now, the caller must
    * decode external to this method. The url is usually generated from a
    * QuerySpec.
    */
  def getList[A <: js.Any](url: String, opts: DynamicsOptions = DefaultDynamicsOptions)(): IO[Seq[A]] =
    getListStream[A](url).compile.toVector

  /**
    * Get a list of values as a stream. Follows @odata.nextLink. For now, the
    * caller must decode external to this method.
    */
  def getListStream[A <: js.Any](url: String, opts: DynamicsOptions = DefaultDynamicsOptions): Stream[IO, A] = {
    val str: Stream[IO, Seq[A]] = Stream.unfoldEval(Option(url)) {
      _ match {
        // Return a IO[Option[(Seq[A],Option[String])]]
        case None => IO.pure(None)
        case Some(nextLink) =>
          val request = HttpRequest(Method.GET, nextLink, headers = toHeaders(opts))
          http.fetch(request) {
            case Status.Successful(resp) =>
              resp.body.map { str =>
                val odata = js.JSON.parse(str).asInstanceOf[ValueArrayResponse[A]]
                if(logger.isDebugEnabled())
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
    str.flatMap(Stream.emits[A])
  }

}
