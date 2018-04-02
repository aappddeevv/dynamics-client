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

  //protected implicit val timer = odelay.js.JsTimer.newTimer

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

  /**
   * Return true if the status *suggests* that the request should be retried.
   * The statuses include: InternalServerError, ServiceUnavailable, BadGateway,
   * GatewayTimeout, TooManyRequests which is a good starting point for
   * dynamics.
   */
  def shouldRetry(s: Status): Boolean = RetriableStatuses.contains(s)

  /**
    * Create a retry policy that retries on `CommunicationFailure`,
    * `DecodeFailure` or UnexpectedStatus (if isRetryableStatus(status) is
    * true) failures.
   * @param policy The retry policy called if failure is detected.
    */
  def retryIfRetryableThrowables[F[_], A](policy: F[A] => F[A],
    isRetryableStatus: Status => Boolean = shouldRetry)
    (implicit F: ApplicativeError[F, Throwable]): F[A] => F[A] =
    fa => F.recoverWith(fa) {
        case c: CommunicationsFailure                                    => policy(fa)
        case d: MessageBodyFailure                                       => policy(fa)
        case u @ UnexpectedStatus(status, _, _) if (isRetryableStatus(status)) => policy(fa)
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
  def makeSomeStatusesErrors[F[_]](isError: Status => Boolean = shouldRetry)
    (implicit F: ApplicativeError[F, Throwable]): Kleisli[F, DisposableResponse[F], DisposableResponse[F]] =
    Kleisli { dr =>
      if (!isError(dr.response.status)) F.pure(dr)
      else F.raiseError(UnexpectedStatus(dr.response.status, None, Some(dr.response)))
    }

  /** 
   * Extract a message from `t` via "getMessage" or the underlying "cause"
   * exception. Use fallback if neither of those found.
   */
  def getMessage(t: Throwable, fallback: String = "no message"): String = {
    (Option(t.getMessage()) orElse Option(t.getCause()).map(_.getMessage()) orElse Option(t.toString()))
      .getOrElse(fallback)
  }

  /**
    * If your F carries an error, retry. If you want to have retry based a bad
    * status, ensure your IO has converted the desired statuses to an effect
    * failure first (see `ensureSeccussfulStatus`).
    */
  def withBackoff[F[_], A](initialDelay: FiniteDuration = 5.seconds, maxRetries: Int = 5)
    (ioa: F[A])(implicit F: ApplicativeError[F, Throwable], timer: Timer[F]): F[A] =
    F.handleErrorWith(ioa) { error =>
      if (maxRetries > 0) {
        val msg = getMessage(error)
        if (logger.isWarnEnabled()) logger.warn(s"Request failed: $msg. Retrying with backoff. ${maxRetries}")
        timer.sleep(initialDelay) *> withBackoff(initialDelay * 2, maxRetries - 1)(ioa)
      } else F.raiseError(error)
    }

  /**
    * If your IO carries an error, retry. If you want to have retry based a bad
    * status, ensure your IO has converted the desired statuses to an effect
    * failure first (see `ensureSeccussfulStatus`).
    */
  def withPause[F[_], A](delayBetween: FiniteDuration = 5.seconds, maxRetries: Int = 5)
    (ioa: F[A])(implicit F: ApplicativeError[F, Throwable], timer: Timer[F]): F[A] =
    F.handleErrorWith(ioa) { error =>
      if (maxRetries > 0) {
        val msg = getMessage(error)
        if (logger.isWarnEnabled()) logger.warn(s"Request failed: $msg. Retrying with pause. ${maxRetries}")
        timer.sleep(delayBetween) *> withPause(delayBetween, maxRetries - 1)(ioa)
      } else F.raiseError(error)
    }

  /**
    * If your IO carries an error, retry. If you want to have retry based a bad
    * status, ensure your IO has converted the desired statuses to an effect
    * failure first (see `ensureSeccussfulStatus`).
    */
  def directly[F[_], A](maxRetries: Int = 5)(ioa: F[A])
    (implicit F: ApplicativeError[F, Throwable]): F[A] =
    F.handleErrorWith(ioa) { error =>
      if (maxRetries > 0) {
        val msg = getMessage(error)
        if (logger.isWarnEnabled()) logger.warn(s"Request failed: $msg. Retrying directly. ${maxRetries}")
        directly(maxRetries - 1)(ioa)
      } else F.raiseError(error)
    }
}

object retry extends Retry

/**
  * Implement retry transparently for a Client as Middleware.  For dynamics,
  * this is a bit hard as the server could be busy but returns 500. Otherwise,
  * other 500 errors maybe or maybe not should be retryable. The retry policies
  * herein are based on specific statuses, specific OS likely retryable errors
  * and errors caused potentially by mangled message bodies.
  *
  *  The new dynamics governer limits are in place and these retry policies take
  *  them into account via status TooManyRequests:
  *  @see https://docs.microsoft.com/en-us/dynamics365/customer-engagement/developer/api-limits
  */
trait RetryMiddleware {

  /**
    * Construct middleware with retry strategies which ensure that responses
    * with statuses known to be retryable are retried. The retry policy is
    * embedded in the "retry" parameter. Therefore, your effect must be able to
    * express "retry" semantics. "retry" is composed with the output from the
    * Middlware's input Client. Your retry strategy may wish to filter on
    * different types of errors so that not every `Throwable` causes a
    * retry. `makeSomeStatusesErrors` is automatically composed.
    */
  def makeMiddleware[F[_]](retry: F[DisposableResponse[F]] => F[DisposableResponse[F]],
    isError: Status => Boolean = http.retry.shouldRetry)
    (implicit F: MonadError[F, Throwable], FM: FlatMap[F]): Middleware[F] =
    client => {
      client.copy(Kleisli { (req: HttpRequest[F]) =>
        (client.open andThen http.retry.makeSomeStatusesErrors[F](isError) mapF retry)(req)
        //(client.open andThen retry.makeSomeStatusesErrors[F](isRetryable) mapF retry)(req)
      }, client.dispose)
    }

  def pause[F[_]](maxRetries: Int = 5, delayBetween: FiniteDuration = 5.seconds)
    (implicit F: MonadError[F, Throwable], timer: Timer[F]) =
    makeMiddleware[F](retry.retryIfRetryableThrowables(retry.withPause(delayBetween, maxRetries)))

  def directly[F[_]](maxRetries: Int = 5)
    (implicit F: MonadError[F, Throwable]) = 
    makeMiddleware[F](retry.retryIfRetryableThrowables(retry.directly(maxRetries)))

  def backoff[F[_]](maxRetries: Int = 5, initialDelay: FiniteDuration = 5.seconds)
    (implicit eh: MonadError[F, Throwable], timer: Timer[F]) =
    makeMiddleware[F](retry.retryIfRetryableThrowables(retry.withBackoff(initialDelay, maxRetries)))
}

object RetryMiddleware extends RetryMiddleware
