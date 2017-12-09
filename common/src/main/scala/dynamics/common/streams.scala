// Copyright (c) 2017 aappddeevv@gmail.com
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package common

import scalajs.js
import scalajs.js._
import fs2._
import fs2.util._
import scala.concurrent._
import duration._
import java.util.concurrent.{TimeUnit => TU}

object fs2helpers {

  /** Use it with `Stream.through`. */
  def liftToPipe[A, B](f: A => Task[B]): Pipe[Task, A, B] = _ evalMap f

  /** Lift f to Sink. Use with `Stream.observe`. */
  def liftToSink[A](f: A => Task[Unit]): Sink[Task, A] = liftToPipe[A, Unit](f)

  /** Log something. Runs f in a Task. */
  def log[A](f: A => Unit) = liftToPipe { a: A =>
    Task.delay { f(a); a }
  }

  /** A pipe that given a stream, delays it by delta. */
  def sleepFirst[F[_], O](delta: FiniteDuration)(implicit F: Async[F], scheduler: Scheduler): Pipe[F, O, O] =
    delayed => time.sleep(delta).flatMap(_ => delayed)

  /** Unfold, periodically checking an effect for new values.
    * Time between checks is obtained using getDelay potentially
    * using the returned effect value. f is run immediately when
    * the stream starts.
    * @param A Output element type.
    * @param f Call an effect to get a value.
    * @param getDelay Extract amount to delay from that value.
    */
  def unfoldEvalWithDelay[A](f: => Task[Option[A]],
                             getDelay: A => FiniteDuration)(implicit s: Strategy, sch: fs2.Scheduler): Stream[Task, A] =
    Stream.unfoldEval(0.seconds) { delay =>
      f.schedule(delay)
        .map { opt =>
          opt.map { a =>
            (a, getDelay(a))
          }
        }
    }

  /** Calculate a delay but use fraction to shorten the delay. */
  def shortenDelay(delay: FiniteDuration, fraction: Double = 0.95): FiniteDuration =
    FiniteDuration((delay * fraction).toSeconds, TU.SECONDS)

  /** Given an effect f, wait until the criteria stop is met.
    *
    * TODO: Add an overall time-limit.
    */
  def pollWait[A](f: => Task[A], stop: A => Boolean, poll: FiniteDuration = 10.seconds)(
      implicit s: Strategy,
      sch: Scheduler): Stream[Task, A] = {
    unfoldEvalWithDelay[A]({
      f.map(a => (stop(a), a)).map { case (stopflag, a) => if (stopflag) None else Some(a) }
    }, _ => poll)
  }

}
