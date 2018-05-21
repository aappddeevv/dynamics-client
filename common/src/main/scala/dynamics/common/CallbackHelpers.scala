// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package common

import scala.scalajs.js
import scalajs.runtime.wrapJavaScriptException
import cats.effect._

/** Some small support to convert callback APIs in node to a cats effect.  This
 * is similar in spirit to io.scalajs.util.PromiseHelper. The first parameter is
 * almost always a subtype of `js.Error`. Most of these methods check the value
 * of the first parameter and if null or undefined, return a failed effect. The
 * function argument is usually a curried function call from a javascript
 * facade.
 */
object CallbackHelpers {

  /** Pass in the function that you only care about the error handler value. The 0
   * means that there are no arguments after the implied "error" first argument.
   * @tparam Z Error type, typically js.Error.
   */
  @inline
  def withError0[F[_], Z](f: js.Function1[Z, js.Any] => Unit)(implicit F: Async[F]) : F[Unit] =
    F.async[Unit]{ cb =>
      f((err: Z) =>
        if (err == null || js.isUndefined(err)) cb(Right(()))
        else cb(Left(wrapJavaScriptException(err)))
      )
    }

  /** Pass in the function that you only care about the error handler value and
   * the value. The signature javascript callback signatre is usually `(err,
   * value)`. Use currying on the scala facade e.g. `someFacadeFunc(arg1, (err,
   * value) => ...)` becomes `withError1[IO,js.Error,Int](someFacadeFunc(arg1, _))`..
   * @tparam Z Error type, typically js.Error.
   * @tparam A The value type.
   */
  @inline
  def withError1[F[_], Z, A](f: js.Function2[Z, A, js.Any] => Unit)(implicit F: Async[F]) : F[A] =
    F.async[A]{ cb =>
      f((err: Z, a: A) =>
        if (err == null || js.isUndefined(err)) cb(Right(a))
        else cb(Left(wrapJavaScriptException(err)))
      )
    }  
}
