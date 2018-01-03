// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package http

import scala.scalajs.js
import scala.concurrent.{Future, ExecutionContext}
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
import fs2helpers._

/** Simple encoder that encodes to a strict value. */
@implicitNotFound("Cannot find instance of EntityEncoder[${A}].")
trait EntityEncoder[A] { self =>

  /** Encode the body. Headers may be dependent on the body so they
    * are returned at the same time to be added to the request.
    */
  def encode(a: A): (Entity, HttpHeaders)

  def contramap[B](f: B => A): EntityEncoder[B] = new EntityEncoder[B] {
    override def encode(a: B): (Entity, HttpHeaders) = self.encode(f(a))
  }
}

object EntityEncoder {

  /** Summon a EntityDecoder from implicit scope. */
  def apply[A](implicit enc: EntityEncoder[A]) = enc
}

trait EntityEncoderInstances {

  implicit val StringEncoder: EntityEncoder[String] = new EntityEncoder[String] {
    def encode(a: String) = (IO.pure(a), HttpHeaders.empty)
  }

  /** scalajs specific */
  implicit val JsDynamicEncoder: EntityEncoder[js.Dynamic] = new EntityEncoder[js.Dynamic] {
    def encode(a: js.Dynamic) = (IO.pure(js.JSON.stringify(a)), HttpHeaders.empty)
  }

  /** scalajs specific */
  def JsObjectEncoder[A <: js.Object]: EntityEncoder[A] = new EntityEncoder[A] {
    def encode(a: A) = (IO.pure(js.JSON.stringify(a)), HttpHeaders.empty)
  }

  /** Implicitly decode anything that is a subclass of js.Object. */
  implicit val jsObjectEncoder = JsObjectEncoder[js.Object]

  /** Explicit lower order priority implicit. */
  implicit def defaultEncoder[A <: js.Object] = JsObjectEncoder[A]

  import play.api.libs.json._

  /** play json */
  implicit val JsValueEncoder: EntityEncoder[JsValue] = new EntityEncoder[JsValue] {
    def encode(a: JsValue) = (IO.pure(Json.stringify(a)), HttpHeaders.empty)
  }

}

/*
trait EntityBody {
  def render(): Task[String]
}
 */
/*
object EntityBody {

  val empty = Task.now("")

  /*
  def strict(s: String) = new EntityBody { def render() = Task.now(s) }
  def apply(t: => Task[String]) = new EntityBody { def render() = t }

  /** scalajs specific */
  def json(json: js.Dynamic) = new EntityBody{ def render() = Task.now(JSON.stringify(json)) }
  /** scalajs specific */
  def jsobj(o: js.Object) = new EntityBody{ def render() = Task.now(JSON.stringify(o)) }

  import play.api.libs.json._

  /** For when you want to keep the value around. */
  case class JsValueEntityBody(val v: JsValue) extends EntityBody {
    def render() = playjson(v).render()
  }
  /** play json */
  def playjson(json: JsValue) = new EntityBody { def render() = Task.now(Json.stringify(json)) }
 */
 }*/

object Entity {
  val empty: Entity = IO.pure("")

  def fromString(s: String): Entity = IO.pure(s)
}
