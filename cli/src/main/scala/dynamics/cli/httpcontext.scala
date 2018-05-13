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
import dynamics.common.instances.io._
import dynamics.common.instances.jsPromise._

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
      val LCID         = config.common.lcid
      implicit val e   = ec
      implicit val sch = Scheduler.default
      implicit val t   = IO.timerGlobal

      private val retryPolicyMiddleware      = makePolicy(config.common)
      private val middleware: Middleware[IO] = makeAuthMiddleware(config.common)(sch, e) andThen retryPolicyMiddleware
      implicit val client: Client[IO]        = middleware(makeHTTPClient(config.common)(e, F, sch))

      def close() = client.dispose

      val appConfig = config
    }
}
