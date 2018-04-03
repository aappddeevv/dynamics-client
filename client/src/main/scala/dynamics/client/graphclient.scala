// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package client

import scala.scalajs.js
import scala.concurrent.ExecutionContext

import cats._
import cats.effect._
import fs2._

import dynamics._
import dynamics.common._
import dynamics.http._
import dynamics.client.common._

/** 
 * Microsoft Graph specific client
 */
case class GraphClient(http: Client[IO], private val connectInfo: ConnectionInfo)
  (implicit F: ApplicativeError[IO,Throwable], ec: ExecutionContext)
    extends LazyLogger with DynamicsClientRequests with ClientMethods {

  def responseToFailedTask[A](resp: HttpResponse[IO], msg: String, req: Option[HttpRequest[IO]]): IO[A] = {
    resp.body.flatMap { body =>
      logger.debug(s"ERROR: ${resp.status}: RESPONSE BODY: $body")
      val statuserror                       = Option(UnexpectedStatus(resp.status, request = req, response = Option(resp)))
      val json                              = js.JSON.parse(body)
      val dynamicserror: Option[GraphErrorJS] = findGraphError(json)
      val derror =
        dynamicserror.map(e => GraphClientError(msg, Some(GraphServerError(e)), statuserror, resp.status))
      val simpleerror = findSimpleMessage(json).map(GraphClientError(_, None, statuserror, resp.status))
      val fallback    = Option(GraphClientError(msg, None, statuserror, resp.status))
      F.raiseError((derror orElse simpleerror orElse fallback).get)
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

  protected def findGraphError(body: js.Dynamic): Option[GraphErrorJS] = {
    if (js.DynamicImplicits.truthValue(body.error)) {
      val error: js.UndefOr[js.Dynamic] = body.error
      error.map(_.asInstanceOf[GraphErrorJS]).toOption
    } else None
  }


    def getList[A <: js.Any](url: String, opts: DynamicsOptions = DefaultDynamicsOptions)(): IO[Seq[A]] =
    _getListStream[A](url, HttpHeaders.empty /*toHeaders(opts)*/).compile.toVector

  /**
    * Get a list of values as a stream. Follows @odata.nextLink. For now, the
    * caller must decode external to this method.
    */
  def getListStream[A <: js.Any](url: String, opts: DynamicsOptions = DefaultDynamicsOptions): Stream[IO, A] =
    _getListStream[A](url, HttpHeaders.empty /*toHeaders(opts)*/)

}
