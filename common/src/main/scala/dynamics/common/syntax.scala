// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package common

import scala.scalajs.js
import js._
import JSConverters._
import io.scalajs.nodejs._
import scala.concurrent._
import io.scalajs.util.PromiseHelper.Implicits._
import fs2._
import cats._
import cats.data._
import cats.implicits._
import cats.effect._
import io.scalajs.npm.chalk._
import js.Dynamic.{literal => jsobj}

final case class StreamOps[F[_], O](s: Stream[F, O]) {
  def vectorChunkN(n: Int): Stream[F, Vector[O]] = s.segmentN(n).map(_.force.toVector)
  def groupBy[O2](f: O => O2)(implicit eq: Eq[O2]): Stream[F, (O2, Vector[O])] =
    s.groupAdjacentBy(f).map(p => (p._1, p._2.force.toVector))
}

trait StreamSyntax {
  implicit def streamToStream[F[_], O](s: Stream[F, O]): StreamOps[F, O] = StreamOps(s)
}

final case class JsAnyOps(a: js.Any) {
  def asJsObj: js.Object        = a.asInstanceOf[js.Object]
  def asDyn: js.Dynamic         = a.asInstanceOf[js.Dynamic]
  def asString: String          = a.asInstanceOf[String]
  def asNumer: Number           = a.asInstanceOf[Number]
  def asInt: Int                = a.asInstanceOf[Int]
  def asDouble: Double          = a.asInstanceOf[Double]
  def asBoolean: Boolean        = a.asInstanceOf[Boolean]
  def asJsArray[A]: js.Array[A] = a.asInstanceOf[js.Array[A]]
  def asJson: String            = js.JSON.stringify(a)
}

trait JsAnySyntax {
  implicit def jsAnyOpsSyntax(a: js.Any) = new JsAnyOps(a)
}

final case class JsObjectOps(o: js.Object) {
  def asDict[A] = o.asInstanceOf[js.Dictionary[A]]
  def asAnyDict = o.asInstanceOf[js.Dictionary[js.Any]]
  def asDyn     = o.asInstanceOf[js.Dynamic]
}

final case class JsDictionaryOps(o: js.Dictionary[_]) {
  def asJsObj = o.asInstanceOf[js.Object]
  def asDyn   = o.asInstanceOf[js.Dynamic]
}

trait JsObjectSyntax {
  implicit def jsObjectOpsSyntax(a: js.Object)           = new JsObjectOps(a)
  implicit def jsDictonaryOpsSyntax(a: js.Dictionary[_]) = new JsDictionaryOps(a)
}

final case class JsUndefOrStringOps(a: UndefOr[String]) {
  def orEmpty: String = a.getOrElse("")
}

/** Not sure this is really going to do much for me. */
final case class JsUndefOrOps[A](a: UndefOr[A]) {
  def isNull  = a == null
  def isEmpty = isNull || !a.isDefined
}

trait JsUndefOrSyntax {
  implicit def jsUndefOrOpsSyntax[A](a: UndefOr[A])   = JsUndefOrOps(a)
  implicit def jsUndefOrStringOps(a: UndefOr[String]) = JsUndefOrStringOps(a)
}

final case class JsDynamicOps(val jsdyn: js.Dynamic) {
  def asString: String            = jsdyn.asInstanceOf[String]
  def asInt: Int                  = jsdyn.asInstanceOf[Int]
  def asArray[A]: js.Array[A]     = jsdyn.asInstanceOf[js.Array[A]]
  def asBoolean: Boolean          = jsdyn.asInstanceOf[Boolean]
  def asJSObj: js.Object          = jsdyn.asInstanceOf[js.Object]
  def asDict[A]: js.Dictionary[A] = jsdyn.asInstanceOf[js.Dictionary[A]]
  def asUndefOr[A]: js.UndefOr[A] = jsdyn.asInstanceOf[js.UndefOr[A]]
  def asJsObjSub[A <: js.Object]  = jsdyn.asInstanceOf[A] // assumes its there!
  def asJsArray[A <: js.Object]   = jsdyn.asInstanceOf[js.Array[A]]
}

trait JsDynamicSyntax {
  implicit def jsDynamicOpsSyntax(jsdyn: js.Dynamic) = JsDynamicOps(jsdyn)
}

object NPMTypes {
  type JSCallbackNPM[A] = js.Function2[io.scalajs.nodejs.Error, A, scala.Any] => Unit
  type JSCallback[A]    = js.Function2[js.Error, A, scala.Any] => Unit

  /** This does not work as well as I thought it would... */
  def callbackToIO[A](f: JSCallbackNPM[A])(implicit e: ExecutionContext): IO[A] = JSCallbackOpsNPM(f).toIO
}

import NPMTypes._

final case class JSCallbackOpsNPM[A](val f: JSCallbackNPM[A]) {

  import scala.scalajs.runtime.wrapJavaScriptException

  /** Convert a standard (err, a) callback to a IO. */
  def toIO(implicit e: ExecutionContext) =
    IO.async { (cb: (Either[Throwable, A] => Unit)) =>
      f((err, a) => {
        if (err == null || js.isUndefined(err)) cb(Right(a))
        else cb(Left(wrapJavaScriptException(err)))
      })
    }
}

trait JSCallbackSyntaxNPM {
  implicit def jsCallbackOpsSyntaxNPM[A](f: JSCallbackNPM[A])(implicit s: ExecutionContext) = JSCallbackOpsNPM(f)
}

trait JsObjectInstances {

  /** Show JSON in its rawness form. Use PrettyJson. for better looking JSON. */
  implicit def showJsObject[A <: js.Object] = Show.show[A] { obj =>
    val sb = new StringBuilder()
    sb.append(Utils.pprint(obj))
    sb.toString
  }
}

/** These are not all implicits. FIXME */
trait JsPromiseSyntax {
  import scala.scalajs.runtime.wrapJavaScriptException

  /** Convert a js.Promise to a IO. */
  implicit class RichPromise[A](p: js.Promise[A]) {
    def toIO(implicit ec: ExecutionContext): IO[A] = {
      val t: IO[A] = IO.async { cb =>
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
      t
    }
  }
}

case class FutureOps[A](val f: Future[A])(implicit ec: ExecutionContext) {
  def toIO: IO[A] = IO.fromFuture(IO(f))
}

trait FutureSyntax {
  implicit def futureToIO[A](f: Future[A])(implicit ec: ExecutionContext) = FutureOps[A](f)
}

case class IteratorOps[A](val iter: scala.Iterator[A])(implicit ec: ExecutionContext) {
  def toFS2Stream[A] = Stream.unfold(iter)(i => if (i.hasNext) Some((i.next, i)) else None)
}

trait IteratorSyntax {
  implicit def toIteratorOps[A](iter: scala.Iterator[A])(implicit ec: ExecutionContext) =
    IteratorOps[A](iter)
}

// Add each individual syntax trait to this
trait AllSyntax
    extends JsDynamicSyntax
    with JSCallbackSyntaxNPM
    with JsUndefOrSyntax
    with JsObjectSyntax
    with JsAnySyntax
    with FutureSyntax
    with IteratorSyntax
    with JsPromiseSyntax
    with StreamSyntax

// Add each individal syntax trait to this
object syntax {
  object all           extends AllSyntax
  object jsdynamic     extends JsDynamicSyntax
  object jscallbacknpm extends JSCallbackSyntaxNPM
  object jsundefor     extends JsUndefOrSyntax
  object jsobject      extends JsObjectSyntax
  object jsany         extends JsAnySyntax
  object future        extends FutureSyntax
  object iterator      extends IteratorSyntax
  object jsPromise     extends JsPromiseSyntax
  object stream        extends StreamSyntax
}

trait AllInstances extends JsObjectInstances

object instances {
  object all      extends AllInstances
  object jsObject extends JsObjectInstances
}

object implicits extends AllSyntax with AllInstances
