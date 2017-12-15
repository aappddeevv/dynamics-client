// Copyright (c) 2017 aappddeevv@gmail.com
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package http

import scala.concurrent.duration._
import scala.scalajs.js
import scala.concurrent._
import fs2._
import dynamics._
import cats.data._
import cats.syntax.show._
import cats._
import cats.effect._
import retry._
import org.slf4j._

import dynamics.common._

/** Implement retry transparently for a Client as Middleware.
  * For dynamics, this is a bit hard as the server could be busy
  * but it returns 500. Otherwise, other 500 errors due to
  * other request issues may retry a bunch of times until they
  * fail but this should only affect developers.
  */
object RetryClient extends LazyLogger {

  import Status._

  protected implicit val timer = odelay.js.JsTimer.newTimer

  protected[this] val RetriableStatuses = Set(
    RequestTimeout,
    InternalServerError, // this is problematic, could be badly composed content!
    ServiceUnavailable,
    BadGateway,
    GatewayTimeout)

  protected def notBad(noisy: Boolean = true) = Success[DisposableResponse] { dr =>
    val x = RetriableStatuses.contains(dr.response.status)
    if (x) { logger.warn(s"Retry: Response: ${dr.response.status.show}.") }
    !x
  }

  protected def policyWithException(policy: Policy) = retry.When {
    case dr: DisposableResponse => policy
    case c: CommunicationsFailure =>
      logger.warn("Retry: " + c.getMessage());
      policy
  }

  def pause(n: Int = 5, pause: FiniteDuration = 5.seconds, noisy: Boolean = true)(implicit e: ExecutionContext): Middleware =
    client(Pause(n, pause), noisy)

  def directly(n: Int = 5, noisy: Boolean = true)(implicit e: ExecutionContext): Middleware =
    client(Directly(n), noisy)

  protected def client(policy: retry.Policy, noisy: Boolean = true)(implicit e: ExecutionContext): Middleware = (c: Client) => {
    implicit val success = notBad(noisy)
    val basePolicy = policyWithException(policy)
    val x: Service[HttpRequest, DisposableResponse] = Kleisli { req: HttpRequest =>
      {
        IO.fromFuture(Eval.always(basePolicy(c.open(req).unsafeToFuture)))
        //IO.fromFuture(Eval.always(basePolicy(c.open(req).unsafeRunAsyncFuture)))
        // Task.fromFuture(policy[DisposableResponse]{ () =>
        //   c.open(req).unsafeRunAsyncFuture
        // })
      }
    }
    c.copy(x, c.dispose)
  }

}
