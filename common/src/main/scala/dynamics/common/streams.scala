// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package common

import scalajs.js
import scalajs.js._
import fs2._
import scala.concurrent._
import duration._
import java.util.concurrent.{TimeUnit => TU}
import cats._
import cats.data._
import cats.implicits._
import cats.effect._

object fs2helpers {

  /** Use it with `Stream.through`. */
  def liftToPipe[A, B](f: A => IO[B]): Pipe[IO, A, B] = _ evalMap f

  /** Lift f to Sink. Use with `Stream.observe`. */
  def liftToSink[A](f: A => IO[Unit]): Sink[IO, A] = liftToPipe[A, Unit](f)

  /** Log something. Runs f in a IO. */
  def log[A](f: A => Unit) = liftToPipe { a: A =>
    IO { f(a); a }
  }

  /** A pipe that given a stream, delays it by delta. */
  //def sleepFirst[F[_], O](delta: FiniteDuration)(implicit F: Effect[F]): Pipe[F, O, O] =
  //   delayed => time.sleep(delta).flatMap(_ => delayed)
  def sleepFirst[F[_], O](
      delta: FiniteDuration)(implicit F: Async[F], s: Scheduler, ec: ExecutionContext): Pipe[F, O, O] =
    delayed => s.delay(delayed, delta)

  /**
    * Unfold, periodically checking an effect for new values.
    * Time between checks is obtained using getDelay potentially
    * using the returned effect value. f is run immediately when
    * the stream starts.
    * @param A Output element type.
    * @param f Call an effect to get a value.
    * @param getDelay Extract amount to delay from that value.
    */
  def unfoldEvalWithDelay[F[_], A](f: => F[Option[A]], getDelay: A => FiniteDuration)(
      implicit M: Functor[F],
      F: Async[F],
      s: Scheduler,
      ec: ExecutionContext): Stream[F, A] =
    Stream.unfoldEval(0.seconds) { delay =>
      M.map(s.effect.delay(f, delay)) { opt =>
        opt.map { a =>
          (a, getDelay(a))
        }
      }
    }
  // Stream.unfoldEval(0.seconds) { delay =>
  //   f.schedule(delay)
  //     .map { opt =>
  //       opt.map { a =>
  //         (a, getDelay(a))
  //       }
  //     }
  // }

  /**
    * Calculate a delay but use fraction to shorten the delay.
    */
  def shortenDelay(delay: FiniteDuration, fraction: Double = 0.95): FiniteDuration =
    FiniteDuration((delay * fraction).toSeconds, TU.SECONDS)

  /**
    * Given an effect F, wait until the criteria stop is met.
    */
  def pollWait[A](f: => IO[A], stop: A => Boolean, poll: FiniteDuration = 10.seconds)(
      implicit sch: Scheduler,
      F: Async[IO],
      ec: ExecutionContext): Stream[IO, A] = {
    unfoldEvalWithDelay[IO, A]({
      f.map(a => (stop(a), a)).map { case (stopflag, a) => if (stopflag) None else Some(a) }
    }, _ => poll)
  }

}
