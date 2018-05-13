// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package client

import scala.scalajs.js
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import js.annotation._
import fs2._
import cats._
import cats.data._
import cats.implicits._
import cats.effect._

import js.JSConverters._
import scala.annotation.implicitNotFound
import scala.collection.mutable

import dynamics.common._
import dynamics.http._
import fs2helpers._
import dynamics.common.instances.jsPromise._

/**
  * ADAL middleware.
  */
object ADALMiddleware extends LazyLogger {

  import java.util.concurrent.{TimeUnit => TU}

  /**
    * Create a Middleware[F] that adds an auth to a Client via Bearer token. A
    * token is immediately obtained then a stream is started to renew tokens so
    * two tokens are obtained. Renewal means obtaining a new token *not* doing a
    * taken refresh.  The retryPolicy is for obtaining auths--not for processing
    * data requests. Internal processing uses IO monad so we need IOtoF and
    * retryPolicy is expressed in terms of IO.
    *
    * @tparam Effect type.
    * @param info Connection information.
    * @param retryPolicy Retry policy for the auth fetches, not the data
    * processing. It uses IO instead of F since its used internally.
    * @param updateAfter Calculate the update delta. Currently ignored.
    * @param sch fs2 Scheduler
    * @param timer cats Timer[F]
    * @param F Error handling for F.
    * @parm IOtoF: Natural transformation from IO (used internally) to F.
    * @return new Middleware and a "stop" effect.
    */
  def apply[F[_]](info: ConnectionInfo,
                  retryPolicy: IO[TokenInfo] => IO[TokenInfo],
                  updateAfter: TokenInfo => FiniteDuration = AuthManager.defaultCalc)(implicit sch: Scheduler,
                                                                                      timer: Timer[F],
                                                                                      F: Async[F],
                                                                                      ec: ExecutionContext,
                                                                                      IOtoF: LiftIO[F]): Middleware[F] =
    (c: Client[F]) => {
      val auth                 = new AuthManager[IO](info)
      val ctx                  = auth.getAuthContext()
      var token: IO[TokenInfo] = auth.getTokenWithRetry(ctx, retryPolicy) // get initial...
      var terminate            = new java.util.concurrent.atomic.AtomicBoolean(false)

      val tokenSetter: Sink[IO, TokenInfo] = _ map { ti =>
        logger.info(s"Setting new token.")
        token = IO.pure(ti)
      }

      var setme: fs2.async.mutable.Signal[IO, Boolean] = null

      // stream runs forever, setting a token into "token".
      // stream runs on IO even though the middleware is F.
      Stream
        .eval(fs2.async.signalOf[IO, Boolean](false))
        .flatMap { stop =>
          setme = stop; // can't do in normal world...
          AuthManager
            .tokenStream[IO](auth.getTokenWithRetry(ctx, retryPolicy),
                             ti => shortenDelay(delay = FiniteDuration(ti.expiresIn.toLong, TU.SECONDS), 0.50))
            .interruptWhen(stop)
            .to(tokenSetter)
        }
        .compile
        .drain
        .unsafeRunAsync(_ => logger.warn("Token renewal stream exited."))

      val dispose =
        if (setme != null) IOtoF.liftIO(setme.set(true))
        else F.pure(())

      val xf: HttpRequest[F] => F[HttpRequest[F]] =
        request =>
          IOtoF.liftIO(token.map { ti =>
            val h = HttpHeaders("Authorization" -> ("Bearer " + ti.accessToken))
            request.copy(headers = h ++ request.headers)
          })
      c.copy(open = c.open compose xf, c.dispose.flatMap(_ => dispose))
    }
}
