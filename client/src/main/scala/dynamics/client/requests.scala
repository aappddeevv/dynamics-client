// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package client

import scala.scalajs.js
import js.annotation._

import dynamics.common._
import dynamics.http._
import dynamics.http.instances.entityEncoder._

trait DynamicsClientRequests {

  val DefaultBatchRequest = HttpRequest(Method.PUT, "/$batch")

  def mkGetListRequest[F[_]](url: String, opts: DynamicsOptions = DefaultDynamicsOptions) =
    HttpRequest[F](Method.GET, url, headers = toHeaders(opts))

  def mkCreateRequest[F[_], B](entitySet: String, body: B, opts: DynamicsOptions = DefaultDynamicsOptions)
    (implicit e: EntityEncoder[B]) = {
    // HttpRequest(Method.POST, s"/$entitySet", body = Entity.fromString(body), headers = toHeaders(opts))
    val (b, h) = e.encode(body)
    HttpRequest[F](Method.POST, s"/$entitySet", body = b, headers = toHeaders(opts) ++ h)
  }

  /** Make a pure delete request. */
  def mkDeleteRequest[F[_]](entitySet: String, keyInfo: DynamicsId, opts: DynamicsOptions = DefaultDynamicsOptions) = {
    val etag =
      if (opts.applyOptimisticConcurrency.getOrElse(false) && opts.version.isDefined)
        HttpHeaders("If-Match" -> opts.version.get)
      else HttpHeaders.empty
    HttpRequest[F](Method.DELETE, s"/$entitySet(${keyInfo.render()})", headers = toHeaders(opts))
  }

  def mkGetOneRequest[F[_]](url: String, opts: DynamicsOptions) = {
    val etag = opts.version.map(etag => HttpHeaders("If-None-Match" -> etag)).getOrElse(HttpHeaders.empty)
    HttpRequest[F](Method.GET, url, headers = toHeaders(opts) ++ etag)
  }

  def mkExecuteActionRequest[F[_]](action: String,
                             body: Entity,
                             entitySetAndId: Option[(String, String)] = None,
                             opts: DynamicsOptions = DefaultDynamicsOptions) = {
    val url = entitySetAndId.map { case (c, i) => s"/$c($i)/$action" }.getOrElse(s"/$action")
    HttpRequest[F](Method.POST, url, body = body, headers = toHeaders(opts))
  }

  /** 
   * This does not handle the version tag + applyOptimisticConcurrency flag yet.
   */
  def toHeaders(o: DynamicsOptions): HttpHeaders = {
    val prefer = OData.render(o.prefers)
    prefer.map(str => HttpHeaders("Prefer"        -> str)).getOrElse(HttpHeaders.empty) ++
    o.user.map(u => HttpHeaders("MSCRMCallerId" -> u)).getOrElse(HttpHeaders.empty)
    //++ o.version.map(etag => HttpHeaders("If-None-Match" -> etag)).getOrElse(HttpHeaders.empty)
  }

  /**
    * Not sure adding $base to the @odata.id is absolutely needed. Probably is.
    * @see https://docs.microsoft.com/en-us/dynamics365/customer-engagement/developer/webapi/associate-disassociate-entities-using-web-api?view=dynamics-ce-odata-9
    */
  def mkAssociateRequest[F[_]](fromEntitySet: String,
                         fromEntityId: String,
                         navProperty: String,
                         toEntitySet: String,
                         toEntityId: String,
                         base: String,
                         singleValuedNavProperty: Boolean = true): HttpRequest[F] = {
    val url  = s"/${fromEntitySet}(${fromEntityId})/$navProperty/$$ref"
    val body = s"""{"@odata.id": "$base/$toEntitySet($toEntityId)"}"""
    val method =
      if (singleValuedNavProperty) Method.PUT
      else Method.POST
    HttpRequest(method, url, body = Entity.fromString(body))
  }

  /**
    * Provide `to` if its a collection-valued navigation property, otherwise it
    * removes a single-valued navigation property.
   * 
   * @see https://docs.microsoft.com/en-us/dynamics365/customer-engagement/developer/webapi/associate-disassociate-entities-using-web-api?view=dynamics-ce-odata-9
    */
  def mkDisassocatiateRequest[F[_]](fromEntitySet: String,
                              fromEntityId: String,
                              navProperty: String,
                              toId: Option[String]): HttpRequest[F] = {
    val navPropertyStr = toId.map(id => s"$navProperty($id)").getOrElse(navProperty)
    val url            = s"/$fromEntitySet($fromEntityId)/$navPropertyStr/$$ref"
    HttpRequest(Method.DELETE, url, body = Entity.empty)
  }

  /**
   * Create a PATCH request that could also upsert. "opts" version could
    * override upsertPreventCreate if a version value is also included, so be careful.
    */
  def mkUpdateRequest[F[_], B](entitySet: String,
                         id: String,
                         body: B,
                         upsertPreventCreate: Boolean = true,
                         upsertPreventUpdate: Boolean = false,
                         options: DynamicsOptions = DefaultDynamicsOptions,
                         base: Option[String] = None)(implicit enc: EntityEncoder[B]): HttpRequest[F] = {
    val (b, xtra) = enc.encode(body)
    val h1 =
      if (upsertPreventCreate) HttpHeaders("If-Match" -> "*")
      else HttpHeaders.empty
    val h2 =
      if (upsertPreventUpdate) HttpHeaders("If-None-Match" -> "*")
      else HttpHeaders.empty
    // this may override If-Match! */
    val h3 =
      if(options.applyOptimisticConcurrency.getOrElse(false) && options.version.isDefined)
        HttpHeaders("If-Match" -> options.version.get)
      else
        HttpHeaders.empty
    val mustHave = HttpHeaders.empty ++ Map("Content-Type" -> Seq("application/json", "type=entry"))
    HttpRequest(Method.PATCH,
                s"${base.getOrElse("")}/$entitySet($id)",
                toHeaders(options) ++ h1 ++ h2 ++ h3 ++ xtra ++ mustHave,
                b)
  }

  def mkExecuteFunctionRequest[F[_]](function: String,
                               parameters: Map[String, scala.Any] = Map.empty,
                               entity: Option[(String, String)] = None) = {
    // (parm, parmvalue)
    val q: Seq[(String, String)] = parameters.keys.zipWithIndex
      .map(x => (x._1, x._2 + 1))
      .map {
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

    val entityPart = entity.map(p => s"/${p._1}(${p._2})").getOrElse("")

    val url = s"$entityPart$functionPart"
    HttpRequest[F](Method.GET, url)
  }

  /** @depecated. Use `mkBatch`. */
  def mkBatchRequest[F[_], A](headers: HttpHeaders, m: Multipart): HttpRequest[F] = mkBatch(m, headers)

  /**
    * Body in HttpRequest is ignored and is instead generated from m.
    * Since the parts will have requests, you need to ensure that the
    * base URL used in those requests have a consistent base URL.
    */
  def mkBatch[F[_], A](m: Multipart, headers: HttpHeaders = HttpHeaders.empty): HttpRequest[F] = {
    import dynamics.http.instances.entityEncoder._
    val (mrendered, xtra) = EntityEncoder[Multipart].encode(m)
    HttpRequest(Method.POST, "/$batch", headers = headers ++ xtra, body = mrendered)
  }
}

object requests extends DynamicsClientRequests
