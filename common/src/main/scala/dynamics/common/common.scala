// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics

import scala.concurrent._
import scala.scalajs.js
import js.|
import fs2._
import cats.~>
import cats.effect._

import scala.scalajs.runtime.wrapJavaScriptException

package object common {

  type JsAnyDict = js.Dictionary[js.Any]

  private[dynamics] def _jsPromiseToIO[A](p: js.Promise[A])(implicit ec: ExecutionContext): IO[A] =
    IO.async { cb =>
      p.`then`[Unit](
        { (v: A) =>
          cb(Right(v))
        },
        js.defined { (e: scala.Any) =>
          // create a Throwable from e
          val t = e match {
            case th: Throwable => th
            case _             => js.JavaScriptException(e)
          }
          cb(Left(t))
        }
      )
      () // return unit
    }

  type Reviver = js.Function2[js.Any, js.Any, js.Any]
}
