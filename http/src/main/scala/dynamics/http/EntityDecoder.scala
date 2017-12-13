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

/**
  * Helper objects to make creating DecodeResults easier. DecodeResult is a complex type.
  */
object DecodeResult {
  def apply[A](fa: Task[Either[DecodeFailure, A]]): DecodeResult[A] = EitherT(fa)
  def success[A](a: Task[A]): DecodeResult[A]                       = DecodeResult(a.map(Either.right(_)))
  def success[A](a: A): DecodeResult[A]                             = success(Task.now(a))
  def failure[A](e: Task[DecodeFailure]): DecodeResult[A]           = DecodeResult(e.map(Either.left(_)))
  def failure[A](e: DecodeFailure): DecodeResult[A]                 = failure(Task.now(e))
  def fail[A]: DecodeResult[A]                                      = failure(Task.now(MessageBodyFailure("Intentionally failed.")))
}

/**
  *  Decode a Message to a DecodeResult.
  */
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
    * Due to the process-once nature of the body, the orElse must
    * really checked headers or other information to allow orElse
    * to compose correctly.
    */
  def orElse[T2 >: T](other: EntityDecoder[T2]): EntityDecoder[T2] = {
    new EntityDecoder[T2] {
      override def decode(msg: Message): DecodeResult[T2] = {
        self.decode(msg) orElse other.decode(msg)
      }
    }
  }

  def widen[T2 >: T]: EntityDecoder[T2] = this.asInstanceOf[EntityDecoder[T2]]

}

object EntityDecoder {

  /** Summon an entity decoder using implicits e.g. `val decoder = EntityDecoder[js.Object]` */
  def apply[T](implicit ev: EntityDecoder[T]): EntityDecoder[T] = ev

  /**
    * Lift function to create a decoder. You can use another EntityDecoder
    * as the arguent.
    */
  def instance[T](run: Message => DecodeResult[T]): EntityDecoder[T] =
    new EntityDecoder[T] {
      def decode(response: Message) = run(response)
    }
}

/**
  * Some EntityDecoder instances specific to the type you want to "output" from
  * the decoding process.
  */
trait EntityDecoderInstances {

  private val reg = """[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}""".r

  /**
    * A decoder that only looks at the header for an OData-EntityId (case-insensitive)
    * value and returns that, otherwise fail. To ensure that the id is returned in the header,
    * you must make sure that return=representation is *not* set in the Prefer headers when
    * the HTTP call is issued.
    */
  val ReturnedIdDecoder: EntityDecoder[String] = EntityDecoder { msg =>
    (msg.headers.get("OData-EntityId") orElse msg.headers.get("odata-entityid"))
      .map(_(0))
      .flatMap(reg.findFirstIn(_))
      .fold(DecodeResult.failure[String](MissingExpectedHeader("OData-EntityId")))(id => DecodeResult.success(id))
  }

  implicit def TextDecoder(implicit s: Strategy): EntityDecoder[String] =
    EntityDecoder { msg =>
      DecodeResult.success(msg.body)
    }

  implicit def JSONDecoder(implicit s: Strategy): EntityDecoder[js.Dynamic] =
    new EntityDecoder[js.Dynamic] {
      def decode(msg: Message) =
        DecodeResult.success(msg.body.map(JSON.parse(_)))
    }

  /** Filter on JSON value. Create DecodeFailure if filter func returns false. */
  def JSONDecoderValidate(f: js.Dynamic => Boolean, failedMsg: String = "Failed validation.")(
      implicit s: Strategy): EntityDecoder[js.Dynamic] = EntityDecoder { msg =>
    JSONDecoder.decode(msg).flatMap { v =>
      if (f(v)) EitherT.right(Task.now(v))
      else EitherT.left(Task.now(MessageBodyFailure(failedMsg)))
    }
  }

  /**
    * Decode the body as json and cast to A instead of JSONDecoder which casts
    * the body to js.Dynamic.
    */
  def JsObjectDecoder[A <: js.Object](implicit s: Strategy): EntityDecoder[A] =
    JSONDecoder.map(_.asInstanceOf[A])

  implicit def jsObjectDecoder[A <: js.Object](implicit s: Strategy) = JsObjectDecoder[A]

  /** Ignore the response. */
  implicit val void: EntityDecoder[Unit] = EntityDecoder { _ =>
    DecodeResult.success(())
  }

  import play.api.libs.json._

  /** play-json */
  implicit val PlayJsonDecoder: EntityDecoder[JsValue] =
    EntityDecoder.instance { msg =>
      DecodeResult.success(msg.body.map(Json.parse(_)))
    }

  /**
    * Check for value array and if there is a value array return the first element.
    * Otherwise cast the entire response to A directly and return it. Either way,
    * return a single value of type `T`.
    *
    * If you are assuming a different underlying decode approach to the raw
    * http body, you need to write your own wrapper to detect the "value" array
    * and decide how to decode based on its presence. That's because its assumed
    * in this function that we will decode to a js.Object first to check for the
    * value array in the response body. This should really be called
    * `FirstElementOfValueArrayIfThereIsOneOrCastWholeMessage`.
    */
  def ValueWrapper[A <: js.Object](implicit s: Strategy) =
    JsObjectDecoder[ValueArrayResponse[A]].flatMapR[A] { arrresp =>
      // if no "value" array, assume its safe to cast to a single A
      arrresp.value.fold(DecodeResult.success(arrresp.asInstanceOf[A]))({ arr =>
        if (arr.size > 0) DecodeResult.success(arr(0))
        else DecodeResult.failure(OnlyOneExpected(s"found ${arr.size}"))
      })
    }

}
