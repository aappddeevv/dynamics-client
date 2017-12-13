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
import dynamics.http.instances.entityEncoder._
import dynamics.http.instances.entityDecoder._

trait DynamicsClientRequests {

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
                         toEntityId: String,
                         base: String): HttpRequest = {
    val url  = s"/${fromEntitySet}(${fromEntityId})/$navProperty/$$ref"
    val body = s"""'data' : {'@odata.id': '$base/$toEntitySet($toEntityId)'}"""
    HttpRequest(Method.PUT, url, body = Entity.fromString(body))
  }

  def mkDisassocatiateRequest(fromEntitySet: String,
                              fromEntityId: String,
                              navProperty: String,
                              to: Option[(String, String)],
                              base: String): HttpRequest = {
    val url = s"/$fromEntitySet($fromEntityId)/$navProperty/$$ref" +
      to.map { case (eset, id) => s"?$$id=$base/$eset($id)" }.getOrElse("")
    HttpRequest(Method.DELETE, url, body = Entity.empty)
  }

  def mkUpdateRequest[A](entitySet: String,
                         id: String,
                         body: A,
                         upsertPreventCreate: Boolean = false,
                         upsertPreventUpdate: Boolean = false,
                         opts: DynamicsOptions = DefaultDynamicsOptions,
                         base: String)(implicit enc: EntityEncoder[A]): HttpRequest = {
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

  /**
    * Body in HttpRequest is ignored and is instead generated from m.
    * Since the parts will have requests, you need to ensure that the
    * base URL used in those requests have a consistent base URL.
    */
  def mkBatchRequest[A](headers: HttpHeaders, m: Multipart): HttpRequest = {
    import dynamics.http.instances.entityEncoder._
    val (mrendered, xtra) = EntityEncoder[Multipart].encode(m)
    HttpRequest(Method.POST, "/$batch", headers = headers ++ xtra, body = mrendered)
  }
}

object DynamicsClientRequests extends DynamicsClientRequests
