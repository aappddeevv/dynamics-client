// Copyright (c) 2017 aappddeevv@gmail.com
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics

import scala.concurrent._
import scala.scalajs.js
import fs2._
import cats.~>
import cats.effect._

package object common {

  def jsPromiseToIO(implicit e: ExecutionContext): js.Promise ~> IO =
    new (js.Promise ~> IO) {
      override def apply[A](p: js.Promise[A]): IO[A] =
        syntax.jsPromise.RichPromise(p).toIO
    }

}
