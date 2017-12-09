// Copyright (c) 2017 aappddeevv@gmail.com
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package http

import scala.scalajs.js
import js.{|, _}
import scala.concurrent.{Future, ExecutionContext}
import js.annotation._
import fs2._
import fs2.util._
import cats._
import cats.data._
import cats.implicits._
import fs2.interop.cats._
import fs2.Task._
import js.JSConverters._
import scala.annotation.implicitNotFound
import scala.collection.mutable

import dynamics.common._
import fs2helpers._

/*
/** Simple encoder that encodes to a strict value. */
@implicitNotFound("Cannot find instance of EntityEncoder[${A}].")
trait EntityEncoder[A] { self =>
  /** Encode the body. Headers may be dependent on the body so they
 * are returned at the same time.
 */
  def encode(a: A): (Task[Entity], HttpHeaders)

  def contramap[B](f: B => A): EntityDecoder[B] = new EntityDecoder[B] {
    override def encode(a: B): (Task[String], HttpHeaders) = self.encode(f(a))
  }
}

object EntityEncoder extends EntityEncoderInstances {
  /** Summon a EntityDecoder. */
  def apply[A](implicit enc: EntityDecoder[A]) = enc
}


trait EntityEncoderInstances {

  implicit val stringEncoder: EntityEncoder[String] = new EntityDecoder {
    def encode(a: String) = (Task.now(a), HttpHeaders.empty)
  }

  //def apply(t: => Task[String]) = new EntityBody { def render() = t }

  /** scalajs specific */
  implicit val jsDynamicEncoder: EntityDecoder[js.Dynamic] = new EntityDecoder {
    def encode(a: js.Dynamic) = (Task.now(JSON.stringify(a)), HttpHeaders.empty)
  }

  //def json(json: js.Dynamic) = new EntityBody{ def render() = Task.now(JSON.stringify(json)) }

  /** scalajs specific */
  implicit val jsObjectEncoder: EntityDecoder[js.Object] = new EntityDecoder {
    def encode(a: js.Object) = (Task.now(JSON.stringify(a)), HttpHeaders.empty)
  }

  //def jsobj(o: js.Object) = new EntityBody{ def render() = Task.now(JSON.stringify(o)) }

  import play.api.libs.json._

  /** For when you want to keep the value around. */
  //case class JsValueEntityBody(val v: JsValue) extends EntityBody {
   // def render() = playjson(v).render()
  //}
  /** play json */

  implicit val jsValueEncoder: EntityDecoder[JsValue] = new EntityDecoder {
    def encode(a: JsValue) = (Task.now(Json.stingify(a)), HttpHeaders.empty)
  }
  //def playjson(json: JsValue) = new EntityBody { def render() = Task.now(Json.stringify(json)) }

}


object DecodeResult {
  def apply[A]( fa: Task[Either[DecodeFailure, A]]): DecodeResult[A] = EitherT(fa)
  def success[A](a: Task[A]): DecodeResult[A] = DecodeResult(a.map(Either.right(_)))
  def success[A](a: A): DecodeResult[A] = success(Task.now(a))
  def failure[A](e: Task[DecodeFailure]): DecodeResult[A] = DecodeResult(e.map(Either.left(_)))
  def failure[A](e: DecodeFailure): DecodeResult[A] = failure(Task.now(e))
  def fail[A]: DecodeResult[A] = failure(Task.now(MessageBodyFailure("Intentionally failed.")))
}

@implicitNotFound("Cannot find instance of EntityDecoder[${T}].")
trait EntityDecoder[T] { self =>

  def apply(response: Message) = decode(response)

  def decode(response: Message): DecodeResult[T]

  def map[T2](f: T => T2): EntityDecoder[T2] = new EntityDecoder[T2] {
    override def decode(msg: Message): DecodeResult[T2] =
      self.decode(msg).map(f)
  }

  def flatMapR[T2](f: T => DecodeResult[T2]): EntityDecoder[T2] = new EntityDecoder[T2] {
    override def decode(msg: Message): DecodeResult[T2] =
      self.decode(msg).flatMap(f)
  }

  /**
 * Due to the process-once nature fo the body, the orElse must
 * really checked headers or other information to allow orElse
 * to compose correctly.
 */
  def orElse[T2 >: T](other: EntityDecoder[T2]): EntityDecoder[T2] = {
    new EntityDecoder[T2] {
      override def decode(msg: Message): DecodeResult[T2] = {
        self.decode(msg) orElse other.decode(msg)
      }
    }}

  def widen[T2 >: T]: EntityDecoder[T2] = this.asInstanceOf[EntityDecoder[T2]]

}

object EntityDecoder extends EntityDecoderInstances {
  /** Summon an entity decoder using implicits. */
  def apply[T](implicit ev: EntityDecoder[T]): EntityDecoder[T] = ev

  /** Lift function to create a decoder. */
  def instance[T](run: Message => DecodeResult[T]): EntityDecoder[T] =
    new EntityDecoder[T] {
      def decode(response: Message) = run(response)
    }
}

trait EntityDecoderInstances {

  /** A decoder that only looks at the header for an OData-EntityId (case-insensitive)
 * value and returns that, otherwise, fail the decode.
 */
  val ReturnedIdDecoder: EntityDecoder[String] = EntityDecoder { msg =>
    (msg.headers.get("OData-EntityId") orElse msg.headers.get("odata-entityid")).
      map(_.mkString(",")).
      fold(DecodeResult.failure[String](
        MissingExpectedHeader("OData-EntityId")))(
        id => DecodeResult.success(id))
  }

  implicit def TextDecoder(implicit s: Strategy): EntityDecoder[String] =
    EntityDecoder { msg => DecodeResult.success(msg.body.render()) }

  implicit def JSONDecoder(implicit s: Strategy): EntityDecoder[js.Dynamic] =
    new EntityDecoder[js.Dynamic] {
      def decode(msg: Message) =
        DecodeResult.success(msg.body.render().map(JSON.parse(_)))
    }

  /** Filter on JSON value. Create DecodeFailure if filter func returns false. */
  def JSONDecoderValidate(f: js.Dynamic => Boolean, failedMsg: String = "Failed validation.")(implicit s: Strategy): EntityDecoder[js.Dynamic] = EntityDecoder{ msg =>
    JSONDecoder.decode(msg).flatMap{ v =>
      if(f(v)) EitherT.right(Task.now(v))
      else EitherT.left(Task.now(MessageBodyFailure(failedMsg)))
    }
  }

  def ObjectDecoder[A <: js.Object](implicit s: Strategy): EntityDecoder[A] =
    JSONDecoder.map(_.asInstanceOf[A])

  /** Ignore the response. */
  implicit val void: EntityDecoder[Unit] = EntityDecoder { _ => DecodeResult.success(()) }

  import play.api.libs.json._

  /*
  implicit def PlayJsonDecoder[A](implicit reader: Reads[A]): EntityDecoder[A] =
    new EntityDecoder[JsValue] {
      val decode(msg: Message) = {
        val v: Task[String] = msg.body.render().map(Json.parse(_))
        //j.validate[A](reader)
        null
      }
    }
 */

}

object EntityDecoders extends EntityDecoderInstances
 */
