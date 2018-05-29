// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package http

import scala.scalajs.js
import cats._
import cats.data._
import cats.implicits._
import cats.effect._
import js.JSConverters._

import dynamics.common._

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

  /** Empty body. */
  val empty: Entity = IO.pure("")

  /** Create an Entity from a strict string value. */
  def fromString(s: String): Entity = IO.pure(s)

  /** Create an Entity from a strict js.Dyanmics value. */
  def fromDynamic(d: js.Dynamic): Entity = IO(js.JSON.stringify(d))

  /** Create an Entity from a strict js.Object or subtype. */
  def fromJSObject[T <: js.Object](d: js.Object): Entity = IO(js.JSON.stringify(d))
}
