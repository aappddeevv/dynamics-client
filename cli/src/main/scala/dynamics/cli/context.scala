// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package cli

import cats._
import cats.data._
import cats.implicits._
import cats.effect._
import scala.concurrent.ExecutionContext
import fs2.Scheduler

import dynamics.http._
import dynamics.client._
import dynamics.common.instances.io._
import dynamics.common.instances.jsPromise._

/** Context for running commands. */
trait Context[F[_]] {
  implicit val e: scala.concurrent.ExecutionContext
  implicit val sch: fs2.Scheduler
  implicit val t: Timer[F]

  def close(): F[Unit]
}

object contextdefaults {
  def makePolicy(common: CommonConfig): Middleware[IO] =
    common.retryPolicy match {
      case "backoff" => RetryMiddleware.backoff[IO](common.numRetries, common.pauseBetween)
      case "directly" => RetryMiddleware.directly[IO](common.numRetries)
      case _         => RetryMiddleware.pause[IO](common.numRetries, common.pauseBetween)
    }

  /** Auth retry is the default from `RetryMiddleware.pause`. */
  def makeAuthMiddleware(common: CommonConfig) 
    (implicit sch: fs2.Scheduler, ec: ExecutionContext): Middleware[IO] =
    ADALMiddleware[IO](common.connectInfo, retry.withPause())

    /** Create a default node-fetch based Client based on the common config. */
  def makeHTTPClient(common: CommonConfig, headers: HttpHeaders = HttpHeaders.empty)
    (implicit ec: ExecutionContext, F: MonadError[IO, Throwable], scheduler: Scheduler): Client[IO] = {
    val fetchOpts = NodeFetchClientOptions(
      timeoutInMillis = common.requestTimeOutInMillis.getOrElse(0),
    )
    NodeFetchClient.create[IO](
      info = common.connectInfo,
      debug = common.debug,
      defaultHeaders =
        client.common.headers.getBasicHeaders() ++ headers ++
          common.impersonate.map(id => HttpHeaders("MSCRMCallerID" -> id)).getOrElse(HttpHeaders.empty),
      options = fetchOpts
    )
  }

}
