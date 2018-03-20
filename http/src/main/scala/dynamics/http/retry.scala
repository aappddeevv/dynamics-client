// Copyright (c) 2017 The Trapelo Group LLC
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
import cats.syntax.all._
import cats._
import cats.effect._
import org.slf4j._

import dynamics.common._


trait Retry extends LazyLogger {
  import Status._

  protected implicit val timer = odelay.js.JsTimer.newTimer

  protected[this] val RetriableStatuses = Set(
    RequestTimeout,
    /** 
     * This is problematic, could be badly composed content! but then would we retry?
     * May have to read error to know which toasts everyone downstream of the retry.
     */
    InternalServerError, 
    ServiceUnavailable,
    BadGateway,
    GatewayTimeout,
    TooManyRequests
  )

  /** Return true if the status *suggests* that the request should be retried. */
  def isRetryable(s: Status): Boolean = RetriableStatuses.contains(s)

  /**
   * Create a retry policy that retries on `CommunicationFailure`,
   * `DecodeFailure` or UnexectedStatus (if isRetryable(status) is
   * true)failures.
   */
  def retryIfRetryableThrowables[F[_], A](retry: F[A] => F[A])
    (implicit eh: ApplicativeError[F, Throwable]): F[A] => F[A] =
    fa => fa.recoverWith {
      case c: CommunicationsFailure => retry(fa)
      case d: MessageBodyFailure => retry(fa)
      case u@UnexpectedStatus(status,_,_) if(isRetryable(status)) => retry(fa)
    }

  /** 
   * Convert an effect containing a `DisposableResponse`, if it fails
   * predictate, to an F UnexpectedStatus error. Otherwise leave it
   * untouched. Success is defined by the predicate. If you have a
   * `Kleisli[F,?,DisposableResponse]` you can obtain a
   * `Kleisli[F,?,DisposableResponse]` using `k1 andThen k2` and ensure your
   * effect carries a failure if the status does not satisfy
   * `predicate`. Callers can use this function if they are not sure that an
   * effect carrying a `DisposableResponse` has converted the effect to contain
   * an error for specific status codes.
   */
  def makeSomeStatusesErrors[F[_]](isError: Status => Boolean = isRetryable)
    (implicit eh: ApplicativeError[F, Throwable]):
      Kleisli[F, DisposableResponse, DisposableResponse] = Kleisli { dr =>
    if(!isError(dr.response.status)) eh.pure(dr)
    else eh.raiseError(UnexpectedStatus(dr.response.status, None, Some(dr.response)))
  }

  /**
   * If your IO carries an error, retry. If you want to have retry based a bad
   * status, ensure your IO has converted the desired statuses to an effect
   * failure first (see `ensureSeccussfulStatus`).
   */ 
  def retryWithBackoff[A](initialDelay: FiniteDuration=5.seconds, maxRetries: Int=5)(ioa: IO[A])
    (implicit timer: Timer[IO]): IO[A] =
    ioa.handleErrorWith { error =>
      if(maxRetries > 0) {
        if(logger.isWarnEnabled()) logger.warn(s"Request failed: ${error.getMessage()}. Retrying with backoff. ${maxRetries}")
          IO.sleep(initialDelay) *> retryWithBackoff(initialDelay * 2, maxRetries - 1)(ioa)
        }
        else IO.raiseError(error)
    }

  /**
   * If your IO carries an error, retry. If you want to have retry based a bad
   * status, ensure your IO has converted the desired statuses to an effect
   * failure first (see `ensureSeccussfulStatus`).
   */ 
  def retryWithPause[A](delayBetween: FiniteDuration=5.seconds, maxRetries: Int=5)(ioa: IO[A])
    (implicit timer: Timer[IO]): IO[A] =
    ioa.handleErrorWith { error =>
      if(maxRetries > 0) {
        if(logger.isWarnEnabled()) logger.warn(s"Request failed: ${error.getMessage()}. Retrying with pause. ${maxRetries}")
        IO.sleep(delayBetween) *> retryWithPause(delayBetween, maxRetries - 1)(ioa)
      }
      else IO.raiseError(error)
    }

  /**
   * If your IO carries an error, retry. If you want to have retry based a bad
   * status, ensure your IO has converted the desired statuses to an effect
   * failure first (see `ensureSeccussfulStatus`).
   */ 
  def retryDirectly[A](maxRetries: Int=5)(ioa: IO[A]): IO[A] =
    ioa.handleErrorWith { error =>
      if(maxRetries > 0) {
        if(logger.isWarnEnabled()) logger.warn(s"Request failed: ${error.getMessage()}. Retrying directly. ${maxRetries}")
        retryDirectly(maxRetries-1)(ioa)
      }
      else IO.raiseError(error)
    }

}

object retry extends Retry


/** 
 * Implement retry transparently for a Client as Middleware.  For dynamics, this
  * is a bit hard as the server could be busy but it returns 500. Otherwise,
  * other 500 errors due to other request issues may retry a bunch of times
  * until they fail but this should only affect developers. The retry policies
  * herein are based on specific status, specific OS (likely) errors and errors
  * caused potentially by mangled message bodies.
  *
  *  The new dynamics governer limits are in place and these retry policies take
  *  them into account via status TooManyRequests:
  *  @see https://docs.microsoft.com/en-us/dynamics365/customer-engagement/developer/api-limits
  */
trait RetryClient extends LazyLogger with Retry {

  // protected def notBad(noisy: Boolean = true) = Success[DisposableResponse] { dr =>
  //   val x = isRetryable(dr.response.status)
  //   if (x) { logger.warn(s"Retry: Response: ${dr.response.status.show}.") }
  //   !x
  // }

  // /**
  //   * A Policy that decides which policy to use based data the data inside the
  //   * Future, whether its a valid value or a failure.
  //   */
  // protected def policyWithException(policy: Policy) = retry.When {
  //   case dr: DisposableResponse => policy
  //   case c: CommunicationsFailure =>
  //     logger.warn("Retry: " + c.getMessage());
  //     policy
  // }

  /**
   * Construct middleware with retry strategies which ensure that responses with
   * statuses known to be retryable are retried. The retry policy is embedded in
   * the "retry" parameter. Therefore, your effect must be able to express
   * "retry" semantics. "retry" is composed with the output from the Middlware's
   * input Client. Your retry strategy may wish to filter on different types of
   * errors so that not every `Throwable` causes a retry.
   */
  def makeMiddleware(retry: IO[DisposableResponse] => IO[DisposableResponse])
      (implicit eh: ApplicativeError[IO, Throwable]): Middleware =
    client => {
      client.copy(Kleisli { req: HttpRequest =>
        (client.open andThen makeSomeStatusesErrors[IO](isRetryable) mapF retry)(req)
      },
        client.dispose)
    }

  def unstable_pause(maxRetries: Int = 5, delayBetween: FiniteDuration = 5.seconds)
    (implicit eh: ApplicativeError[IO, Throwable]): Middleware =
    makeMiddleware(retryIfRetryableThrowables(retryWithPause(delayBetween, maxRetries)))

  def unstable_directly(maxRetries: Int = 5)
    (implicit eh: ApplicativeError[IO, Throwable]): Middleware =
    makeMiddleware(retryIfRetryableThrowables(retryDirectly(maxRetries)))

  def unstable_backoff(maxRetries: Int = 5, initialDelay: FiniteDuration = 5.seconds)
    (implicit eh: ApplicativeError[IO, Throwable]): Middleware =
    makeMiddleware(retryIfRetryableThrowables(retryWithBackoff(initialDelay, maxRetries)))

  // /** Middlaware based on a retry.Pause policy. */
  // def pause(n: Int = 5, pause: FiniteDuration = 5.seconds, noisy: Boolean = true)(
  //     implicit e: ExecutionContext): Middleware =
  //   middleware(Pause(n, pause), noisy)

  // /** Middleware based on retry.Directly policy. */
  // def directly(n: Int = 5, noisy: Boolean = true)(implicit e: ExecutionContext): Middleware =
  //   middleware(Directly(n), noisy)

  // /** Middleware based on retry.Backup policy. */
  // def backoff(n: Int = 5, initialPause: FiniteDuration = 5.seconds, noisy: Boolean = true)(
  //     implicit e: ExecutionContext): Middleware =
  //   middleware(Backoff(n, initialPause), noisy)

  // /** Create a Middleware based on a retry policy. */
  // protected def middleware(policy: retry.Policy, noisy: Boolean = true)(implicit e: ExecutionContext): Middleware =
  //   (c: Client) => {
  //     implicit val success = notBad(noisy)
  //     val basePolicy       = policyWithException(policy)
  //     val x: Service[HttpRequest, DisposableResponse] = Kleisli { req: HttpRequest =>
  //       {
  //         IO.fromFuture(IO(basePolicy(c.open(req).unsafeToFuture)))
  //         //IO.fromFuture(Eval.always(basePolicy(c.open(req).unsafeRunAsyncFuture)))
  //         // Task.fromFuture(policy[DisposableResponse]{ () =>
  //         //   c.open(req).unsafeRunAsyncFuture
  //         // })
  //       }
  //     }
  //     c.copy(x, c.dispose)
  //   }

}


object RetryClient extends RetryClient
