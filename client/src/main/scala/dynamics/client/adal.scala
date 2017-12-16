// Copyright (c) 2017 aappddeevv@gmail.com
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
import retry._

import dynamics.common._
import dynamics.http._
import fs2helpers._

/**
  * Middleware: Add auth to a Client via Bearer token. A token is
  * immediately obtained then a stream is started to renew tokens
  * so two tokens are obtained.
  *
  * Token renewal only works on node for the moment.
  */
object ADAL extends LazyLogger {

  import java.util.concurrent.{TimeUnit => TU}

  def apply(info: ConnectionInfo, retryPolicy: Policy = Pause(3, 2.seconds))(implicit sch: Scheduler,
                                                                             e: ExecutionContext): Middleware =
    (c: Client) => {
      val auth                 = new AuthManager(info)
      val ctx                  = auth.getAuthContext()
      var token: IO[TokenInfo] = auth.getToken(ctx) // get initial...
      var terminate            = new java.util.concurrent.atomic.AtomicBoolean(false)

      val tokenSetter: Sink[IO, TokenInfo] = _ map { ti =>
        logger.info(s"Setting new token.")
        token = IO.pure(ti)
      }

      var setme: fs2.async.mutable.Signal[IO, Boolean] = null

      Stream
        .eval(fs2.async.signalOf[IO, Boolean](false))
        .flatMap { stop =>
          setme = stop; // can't do in normal world...
          AuthManager
            .tokenStream(auth.getTokenWithRetry(ctx, retryPolicy), _ => FiniteDuration(55, TU.MINUTES))
            .interruptWhen(stop)
            .to(tokenSetter)
        }
        .run
        .unsafeRunAsync(_ => logger.warn("Token renewal stream exited."))

      val dispose = IO {
        if (setme != null) setme.set(true)
        else IO.pure(())
      }.flatten

      val xf: HttpRequest => IO[HttpRequest] = (request: HttpRequest) =>
        token.map { ti =>
          val h = HttpHeaders("Authorization" -> ("Bearer " + ti.accessToken))
          request.copy(headers = h ++ request.headers)
      }

      c.copy(open = c.open compose xf, c.dispose.flatMap(_ => dispose))
    }
}
