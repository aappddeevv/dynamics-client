// Copyright (c) 2017 aappddeevv@gmail.com
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package client

import scala.language.higherKinds
import scalajs.js
import js._
import scala.util.{Try, Failure}
import scala.concurrent._
import fs2._
import cats._
import cats.effect._

import dynamics.common._

import dynamics.http.ConnectionInfo

private[dynamics] object ADALHelpers {

  // http://www.mikaelmayer.com/2015/08/07/converting-javascript-to-scala-on-scala-js/
  implicit class OrIfNull[T](s: T) {
    def orIfNull(e: T): T = if (s == null) e else s
  }

  def toMessage(er: ErrorResponse) = er.error + ": " + er.errorDescription

  def toMessage(er: js.Error) =
    if (er == null) "No error message provided."
    else er.name.orIfNull("") + er.message

  /** Unwind the return value int an effect. */
  def acquireTokenWithUsernamePassword(ctx: AuthenticationContext,
                                       resource: String,
                                       username: String,
                                       password: String,
                                       applicationId: String)(implicit ec: ExecutionContext): IO[TokenInfo] = {
    IO.async { (cb: Either[Throwable, TokenInfo] => Unit) =>
      ctx.acquireTokenWithUsernamePassword(
        resource,
        username,
        password,
        applicationId,
        (err, resp) => {
          if (err != null) cb(Left(new TokenRequestError(toMessage(err))))
          else if (!resp.isDefined) cb(Left(new TokenRequestError("Unknown error")))
          else {
            resp.get match {
              case response if response.merge[js.Object].hasOwnProperty("accessToken") =>
                cb(Right(response.asInstanceOf[TokenInfo]))
              case response =>
                cb(Left(new TokenRequestError(toMessage(response.asInstanceOf[ErrorResponse]))))
            }
          }
        }
      )
    }
  }
}

/**
  * Convenience functions to work with ADAL tokens. If tenant and authorityHostUrl are undefined,
  * it is attempted to derive them from username (the demain part) and using a default authority hostname
  * `https://login.windows.net`. If acquireTokenResource is undefined, dataUrl is tried in its place.
  */
class AuthManager(info: ConnectionInfo)(implicit ehandler: ApplicativeError[IO,Throwable], scheduler: Scheduler) extends LazyLogger {

  require(
    info.username.isDefined &&
      info.password.isDefined &&
      info.applicationId.isDefined &&
      info.dataUrl.isDefined)

  import fs2._
  import async._
  import fs2helpers._
  import java.util.concurrent.{TimeUnit => TU}
  import scala.concurrent.duration._
  import retry._

  // Fill in missing information.
  val tenant = info.tenant orElse info.username.flatMap("^.+@(.+)$".r.findFirstMatchIn(_).map(_.group(1)))
  val authority = (info.authorityHostUrl.toOption orElse
    Some("https://login.windows.net")) map (_ + "/" + tenant)
  val tokenResource = (info.acquireTokenResource orElse info.dataUrl)

  /** Obtain an AuthenticatonContext. Throw TokenRequestError
    * on failure.
    */
  def getAuthContext() = Try { new AuthenticationContext(authority.get) } match {
    case scala.util.Success(c) => c
    case Failure(t) =>
      println(s"Failed to obtain auth token: $t")
      throw new TokenRequestError(s"Unable to create authentication context using authority $authority: $t")
  }

  /** Get a token wrapped in an effect. */
  def getToken(ctx: AuthenticationContext)(implicit ec: ExecutionContext): IO[TokenInfo] =
    ADALHelpers.acquireTokenWithUsernamePassword(ctx,
                                                 tokenResource.get,
                                                 info.username.get,
                                                 info.password.get,
                                                 info.applicationId.get)

  def policyWithException(policy: Policy) = retry.When {
    case t: TokenRequestError =>
      logger.warn("Retry: " + t.getMessage())
      policy
    case _ => policy
  }

  import retry.Success.either

  /** Get a token but use the specified retry policy. */
  def getTokenWithRetry(ctx: AuthenticationContext, policy: Policy)(implicit e: ExecutionContext): IO[TokenInfo] = {
    IO.fromFuture(Eval.always(policyWithException(policy)(getToken(ctx).attempt.unsafeToFuture()))).flatMap { e =>
      e match {
        case Right(ti) => IO.pure(ti)
        case Left(t)   => ehandler.raiseError(t)
      }
    }
  }

}

object AuthManager {
  import fs2._
  import async._
  import fs2helpers._
  import java.util.concurrent.{TimeUnit => TU}
  import scala.concurrent.duration._

  private val _calc: TokenInfo => FiniteDuration =
    ti => { shortenDelay(delay = FiniteDuration(ti.expiresIn, TU.SECONDS)) }

  /** Stream of TokenInfo. Default is to renew at 95% of expiration time. */
  def tokenStream(f: => IO[TokenInfo],
    calc: TokenInfo => FiniteDuration = _calc)
    (implicit F: Async[IO], sch: Scheduler, ec: ExecutionContext) =
    unfoldEvalWithDelay[IO, TokenInfo](F.map(F.attempt(f))(_.toOption), calc)

}
