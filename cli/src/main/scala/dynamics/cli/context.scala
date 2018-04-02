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

import client.DynamicsClient
import dynamics.http._
import dynamics.client._
import common.instances.io._
import common.instances.jsPromise._

/** Context for running commands. */
trait Context[F[_]] {
  implicit val e: scala.concurrent.ExecutionContext
  implicit val sch: fs2.Scheduler
  implicit val t: Timer[F]
}

/** Dynamics context. */
trait DynamicsContext extends Context[IO] {
  /** Set your own LCID e.g. US English is 1033. */
  def LCID: Int
  implicit val dynclient: DynamicsClient
  /** Execute to close. */
  def close(): IO[Unit]
  /** The overall configuration for all plugins/modules. */
  def appConfig: AppConfig
}

object DynamicsContext {
  import contextdefaults._

  /**
    * Create a default DynamicsContext. Middleware for request retry and ADAL
    * are automatically addded. Allowed policies are ''backoff'' and any other
    * policy string is translated into a ''pause'' policy.
    */
  def default(config: AppConfig)(implicit ec: ExecutionContext, F: MonadError[IO, Throwable]): DynamicsContext =
    new DynamicsContext {
      import dynamics.client._
      val LCID = config.common.lcid
      implicit val e   = ec
      implicit val sch = Scheduler.default
      implicit val t = IO.timer(e)

      private val retryPolicyMiddleware = makePolicy(config.common)
      private val middleware = makeAuthMiddleware(config.common)(sch, e) andThen retryPolicyMiddleware
      implicit val httpclient =  middleware(makeHTTPClient(config.common)(e, F, sch))
      implicit val dynclient: DynamicsClient =
        DynamicsClient(httpclient, config.common.connectInfo, config.common.debug)(F, e)

      def close() = httpclient.dispose

      val appConfig = config
    }
}


/**
 * General HTTP context that is not dynamics specific. 
 */
trait HTTPContext extends Context[IO] {
  def LCID: Int
  implicit val client: http.Client[IO]
  def close(): IO[Unit]
  def appConfig: AppConfig
}

object HTTPContext {

  import contextdefaults._

  /**
    * Create a default HTTPContext. Middleware for request retry and ADAL
    * are automatically addded. Allowed policies are ''backoff'' and any other
    * policy string is translated into a ''pause'' policy.
    */
  def default(config: AppConfig)(implicit ec: ExecutionContext, F: MonadError[IO, Throwable]): HTTPContext =
    new HTTPContext {
      import dynamics.client._
      val LCID = config.common.lcid
      implicit val e   = ec
      implicit val sch = Scheduler.default
      implicit val t = IO.timer(e)

      private val retryPolicyMiddleware = makePolicy(config.common)
      private val middleware: Middleware[IO] = makeAuthMiddleware(config.common)(sch, e) andThen retryPolicyMiddleware
      implicit val client: Client[IO] =  middleware(makeHTTPClient(config.common)(e, F, sch))

      def close() = client.dispose

      val appConfig = config
    }
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
  def makeHTTPClient(common: CommonConfig)
    (implicit ec: ExecutionContext, F: MonadError[IO, Throwable], scheduler: Scheduler): Client[IO] = {
    val fetchOpts = NodeFetchClientOptions(timeoutInMillis = common.requestTimeOutInMillis.getOrElse(0))
    NodeFetchClient.create[IO](
      info = common.connectInfo,
      debug = common.debug,
      defaultHeaders =
        common.impersonate.map(id => HttpHeaders("MSCRMCallerID" -> id)).getOrElse(HttpHeaders.empty),
      options = fetchOpts
    )
  }

}
