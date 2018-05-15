// Copyright (c) 2017 The Trapelo Group LLC
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

  /** Unwind the return value into an effect. */
  def acquireTokenWithUsernamePassword[F[_]](
      ctx: AuthenticationContext,
      resource: String,
      username: String,
      password: String,
    applicationId: String)
    (implicit FAsync: Async[F], PtoF: js.Promise ~> F): F[TokenInfo] = {
    FAsync.async { (cb: Either[Throwable, TokenInfo] => Unit) =>
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

  def acquireTokenWithClientCredentials[F[_]](
      ctx: AuthenticationContext,
    resource: String,
    secret: String,          
    applicationId: String)
    (implicit FAsync: Async[F], PtoF: js.Promise ~> F): F[TokenInfo] = {
    FAsync.async { (cb: Either[Throwable, TokenInfo] => Unit) =>
      ctx.acquireTokenWithClientCredentials(
        resource,
        applicationId,        
        secret,
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
class AuthManager[F[_]](info: ConnectionInfo)(implicit F: Async[F], PtoF: scalajs.js.Promise ~> F) extends LazyLogger {
  //, scheduler: Scheduler
  require(
    ((info.username.isDefined && info.password.isDefined) || (info.secret.isDefined)) &&
      info.applicationId.isDefined &&
      info.dataUrl.isDefined)

  import fs2._
  import async._
  import fs2helpers._
  import java.util.concurrent.{TimeUnit => TU}
  import scala.concurrent.duration._
  //import retry._

  // Fill in missing information.
  val tenant = info.tenant orElse info.username.flatMap("^.+@(.+)$".r.findFirstMatchIn(_).map(_.group(1)))
  val authority = (info.authorityHostUrl.toOption orElse
    Some("https://login.windows.net")) map (_ + "/" + tenant)
  val tokenResource = (info.acquireTokenResource orElse info.dataUrl)
  /** true if we should use username/password, false use client credentials. */
  val hasUsernameAndPassword = info.username.isDefined && info.password.isDefined

  /** Obtain an AuthenticatonContext. Throw TokenRequestError
    * on failure.
    */
  def getAuthContext() =
    Try {
      new AuthenticationContext(authority.get)
    } match {
      case scala.util.Success(c) => c
      case Failure(t) =>
        println(s"Failed to obtain auth token: $t")
        throw new TokenRequestError(s"Unable to create authentication context using authority $authority: $t")
    }

  /** Get a token wrapped in an effect. No retry is performed if the request fails. */
  def getToken(ctx: AuthenticationContext): F[TokenInfo] =
    if(hasUsernameAndPassword)
      ADALHelpers.acquireTokenWithUsernamePassword[F](ctx,
        tokenResource.get,
        info.username.get,
        info.password.get,
        info.applicationId.get)
    else {
      ADALHelpers.acquireTokenWithClientCredentials[F](ctx,
        tokenResource.get,
        info.secret.get,        
        info.applicationId.get)
    }

  /** Get a token with a potential retry. */
  def getTokenWithRetry(ctx: AuthenticationContext, retryPolicy: F[TokenInfo] => F[TokenInfo]): F[TokenInfo] =
    retryPolicy(getToken(ctx))

  /** Get a token stream. */
  def tokenStream(ctx: AuthenticationContext,
                  retryPolicy: F[TokenInfo] => F[TokenInfo],
                  calc: TokenInfo => FiniteDuration = AuthManager.defaultCalc)(implicit timer: Timer[F]) =
    AuthManager.tokenStream(getTokenWithRetry(ctx, retryPolicy), calc)
}

object AuthManager {
  import fs2._
  import async._
  import fs2helpers._
  import java.util.concurrent.{TimeUnit => TU}
  import scala.concurrent.duration._

  val defaultCalc: TokenInfo => FiniteDuration =
    ti => shortenDelay(delay = FiniteDuration(ti.expiresIn.toLong, TU.SECONDS))

  /**
    * Stream of TokenInfo. Default is to renew at 95% of expiration time.
    * @param f Create a token. Generally from `AuthManager.getTokenWithRetry`.
    * @param calc Calculate when to get the next token.
    * @return Stream that provides auth tokens.
    */
  def tokenStream[F[_]](f: => F[TokenInfo], calc: TokenInfo => FiniteDuration = defaultCalc)(implicit F: Async[F],
                                                                                             timer: Timer[F]) =
    unfoldEvalWithDelay[F, TokenInfo](F.map(F.attempt(f))(_.toOption), calc)

}
