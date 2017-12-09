// Copyright (c) 2017 aappddeevv@gmail.com
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package common

import io.monadless._
import fs2._

trait MonadlessTask extends Monadless[Task] {

  def collect[T](list: List[Task[T]]): Task[List[T]] =
    Task.traverse(list)(identity).map(_.toList) //Future.sequence(list)

  def rescue[T](m: Task[T])(pf: PartialFunction[Throwable, Task[T]]): Task[T] =
    m.handleWith(pf) //m.recoverWith(pf)

  /*
  def ensure[T](m: Task[T])(f: => Unit): Task[T] = {
    m.onComplete(_ => f)
    m
  }
 */

}

object MonadlessTask extends MonadlessTask

import cats._
import cats.data._
import cats.implicits._
import cats.effect._

trait MonadlessIO extends Monadless[IO] {
  def collect[T](list: List[IO[T]]): IO[List[T]] = list.sequence

  def rescue[T](m: IO[T])(pf: PartialFunction[Throwable, IO[T]]): IO[T] =
    //m.handleWith(pf) //m.recoverWith(pf)
    m.attempt.flatMap {
      case Left(e)  => pf.lift(e) getOrElse IO.raiseError(e)
      case Right(a) => IO.pure(a)
    }
}
