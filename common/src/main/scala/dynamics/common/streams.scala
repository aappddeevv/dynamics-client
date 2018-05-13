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

  /**
    * from cats-effect gitter channel
    */
  def parallelWithLimit[A](limit: Int, as: List[IO[A]]) =
    as.grouped(limit).toList.flatTraverse(_.parSequence)

  /** Given a list of Fs, eval them at most N at a time. Output order is not preserved. */
  def evalN[F[_], A](fs: Seq[F[A]], n: Int = 10)(implicit F: Effect[F], ec: ExecutionContext): F[Vector[A]] =
    Stream.emits(fs.map(Stream.eval(_))).join(n).compile.toVector

  /** Use it with `Stream.through`. */
  def liftToPipe[A, B](f: A => IO[B]): Pipe[IO, A, B] = _ evalMap f

  /** Lift f to Sink. Use with `Stream.observe`. */
  def liftToSink[A](f: A => IO[Unit]): Sink[IO, A] = liftToPipe[A, Unit](f)

  /** Log something. Runs f in a IO. */
  def log[A](f: A => Unit) = liftToPipe { a: A =>
    IO { f(a); a }
  }

  /** A pipe that given a stream, delays it by delta. */
  def sleepFirst[F[_], O](
      delta: FiniteDuration)(implicit F: Async[F], s: Scheduler, ec: ExecutionContext): Pipe[F, O, O] =
    delayed => s.delay(delayed, delta)

  /**
    * Unfold, periodically checking an effect for new values.  Time between
    * checks is obtained using getDelay potentially using the returned effect
    * value. f is run immediately when the stream starts. The delay originates
    * from `Timer[F]` (on the F async functor) and not from a fs2 scheduler.
    *
    * @param A Output element type.
    * @param f Call an effect to get a value.
    * @param getDelay Extract amount to delay from that value.
    */
  def unfoldEvalWithDelay[F[_], A](f: => F[Option[A]], getDelay: A => FiniteDuration)(implicit M: Functor[F],
                                                                                      F: Async[F],
                                                                                      t: Timer[F]): Stream[F, A] =
    Stream.unfoldEval(0.seconds) { delay =>
      M.map(t.sleep(delay) *> f) { opt =>
        //M.map(s.effect.delay(f, delay)) { opt =>
        opt.map { a =>
          (a, getDelay(a))
        }
      }
    }

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

  /** Throttle a stream. Default value is for dynamics throttling. */
  def throttle[F[_], A](
      delay: FiniteDuration = 5.millis)(implicit sch: Scheduler, ec: ExecutionContext, F: Effect[F]): Pipe[F, A, A] =
    _.zip(sch.awakeEvery[F](delay)).map(_._1)
  // def mapAsync[F[_]: Effect, A, B](parallelism: Int)(f: A => F[B])(implicit executionContext: ExecutionContext): Pipe[F, A, B] =
  // { stream => Stream.eval(fs2.async.mutable.Queue.bounded[F, Option[F[Either[Throwable, B]]]](parallelism)).flatMap { q =>
  //     q.dequeueAvailable
  //       .unNoneTerminate
  //       .evalMap(identity)
  //       .rethrow
  //       .concurrently {
  //         stream.evalMap { a =>
  //           scala.concurrent.Promise.empty[F, Either[Throwable, B]].flatMap { promise =>
  //             val sb = Stream.eval(f(a).attempt).evalMap(promise.complete)
  //             q.enqueue1(Some(promise.get)).as(sb)
  //           }
  //         }.join(parallelism).drain ++ Stream.eval(q.enqueue1(None))
  //       }
  //   }
  // }

}
