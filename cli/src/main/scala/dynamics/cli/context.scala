// Copyright (c) 2017 aappddeevv@gmail.com
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package cli
import cats.effect.IO

import client.DynamicsClient

/** Context for running commands. */
trait Context {
  implicit val e: scala.concurrent.ExecutionContext
  implicit val sch: fs2.Scheduler
}

/** Dynamics context. */
trait DynamicsContext extends Context {

  /** Set your own LCID. Default is US English. */
  val LCID: Int = 1033

  implicit val dynclient: DynamicsClient

  /** Execute to close. */
  def close(): IO[Unit]
}

object DynamicsContext {

  import scala.concurrent.ExecutionContext
  import fs2._
  import dynamics.http._
  import dynamics.client._

  /** Create a default DynamicsContext. */
  def default(config: AppConfig)(implicit e: ExecutionContext): DynamicsContext =
    new DynamicsContext {
      import dynamics.client._
      implicit val e   = scala.concurrent.ExecutionContext.Implicits.global
      implicit val sch = Scheduler.default

      val fetchOpts = NodeFetchClientOptions(timeoutInMillis = config.common.requestTimeOutInMillis.getOrElse(0))

      val httpclient: Client =
        (RetryClient.pause(config.common.numRetries, config.common.pauseBetween) andThen
          ADAL(config.common.connectInfo))(
          NodeFetchClient.newClient(config.common.connectInfo, config.common.debug, opts = fetchOpts))

      implicit val dynclient: DynamicsClient =
        DynamicsClient(httpclient, config.common.connectInfo, config.common.debug)

      def close() = httpclient.dispose
    }
}
