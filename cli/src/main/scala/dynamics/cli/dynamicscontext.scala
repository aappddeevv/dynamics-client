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

/** Dynamics context. */
trait DynamicsContext extends Context[IO] {
  def LCID: Int
  implicit val dynclient: DynamicsClient
  def appConfig: AppConfig
}

object DynamicsContext {
  import contextdefaults._

  /**
    * Create a default DynamicsContext. Middleware for request retry and ADAL
    * are automatically addded. Allowed policies are ''backoff'' and any other
    * policy string is translated into a ''pause'' policy.
    */
  def default(config: AppConfig, headers: HttpHeaders= HttpHeaders.empty)
    (implicit ec: ExecutionContext, F: MonadError[IO, Throwable]): DynamicsContext =
    new DynamicsContext {
      import dynamics.client._
      val LCID = config.common.lcid
      implicit val e   = ec
      implicit val sch = Scheduler.default
      implicit val t = IO.timerGlobal

      private val retryPolicyMiddleware = makePolicy(config.common)
      private val middleware = makeAuthMiddleware(config.common)(sch, e) andThen retryPolicyMiddleware
      implicit val httpclient =  middleware(makeHTTPClient(config.common, headers)(e, F, sch))
      implicit val dynclient: DynamicsClient =
        DynamicsClient(httpclient, config.common.connectInfo, config.common.debug)(F, e)

      def close() = httpclient.dispose

      val appConfig = config
    }
}
