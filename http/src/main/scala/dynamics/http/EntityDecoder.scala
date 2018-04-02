// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package http

import scala.scalajs.js
import scala.concurrent.ExecutionContext
import js.annotation._
import fs2._
import cats._
import cats.data._
import cats.effect._
import cats.implicits._
import js.JSConverters._
import scala.annotation.implicitNotFound
import scala.collection.mutable

import dynamics.common._

/**
  *  Decode a Message to a DecodeResult.
  */
@implicitNotFound("Cannot find instance of EntityDecoder[${T}].")
trait EntityDecoder[F[_], T] { self =>

  def apply(response: Message[F]): DecodeResult[F, T] = decode(response)

  def decode(response: Message[F]): DecodeResult[F, T]

  def map[T2](f: T => T2)(implicit F: Functor[F]): EntityDecoder[F, T2] =
    new EntityDecoder[F, T2] {
      override def decode(msg: Message[F]): DecodeResult[F, T2] =
        self.decode(msg).map(f)
    }

  def flatMapR[T2](f: T => DecodeResult[F, T2])(implicit F: Monad[F]): EntityDecoder[F, T2] =
    new EntityDecoder[F, T2] {
      override def decode(msg: Message[F]): DecodeResult[F, T2] =
        self.decode(msg).flatMap(f)
    }

  /**
    * Due to the process-once nature of the body, the orElse must
    * really check headers or other information to allow orElse
    * to compose correctly.
    */
  def orElse[T2 >: T](other: EntityDecoder[F, T2])(implicit F: Monad[F]): EntityDecoder[F, T2] = {
    new EntityDecoder[F, T2] {
      override def decode(msg: Message[F]): DecodeResult[F, T2] = {
        self.decode(msg) orElse other.decode(msg)
      }
    }
  }

  def widen[T2 >: T]: EntityDecoder[F, T2] = this.asInstanceOf[EntityDecoder[F, T2]]

  def transform[T2](t: Either[DecodeFailure, T] => Either[DecodeFailure, T2])
    (implicit F: Functor[F]): EntityDecoder[F, T2] =
    new EntityDecoder[F, T2] {
      override def decode(message: Message[F]): DecodeResult[F, T2] =
        self.decode(message).transform(t)
    }

}

object EntityDecoder {

  /** Summon an entity decoder using implicits e.g. `val decoder = EntityDecoder[js.Object]` */
  def apply[F[_], T](implicit ev: EntityDecoder[F, T]): EntityDecoder[F, T] = ev

  /**
    * Lift function to create a decoder. You can use another EntityDecoder
    * as the argument.
    */
  def instance[F[_], T](run: Message[F] => DecodeResult[F, T]): EntityDecoder[F, T] =
    new EntityDecoder[F, T] {
      def decode(response: Message[F]) = run(response)
    }
}

/**
  * Some EntityDecoder instances specific to the type you want to "output" from
  * the decoding process. Currently tied to `IO`.
  */
trait EntityDecoderInstances {

  private val reg = """[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}""".r

  /**
    * A decoder that only looks at the header for an OData-EntityId (case-insensitive)
    * value and returns that, otherwise fail. To ensure that the id is returned in the header,
    * you must make sure that return=representation is *not* set in the Prefer headers when
    * the HTTP call is issued.
    */
  val ReturnedIdDecoder: EntityDecoder[IO, String] = EntityDecoder { msg =>
    (msg.headers.get("OData-EntityId") orElse msg.headers.get("odata-entityid"))
      .map(_(0))
      .flatMap(reg.findFirstIn(_))
      .fold(DecodeResult.failure[IO,String](MissingExpectedHeader("OData-EntityId")))(id => DecodeResult.success(id))
  }

  /** Body is just text. One of the simplest decoders. */
  implicit def TextDecoder(implicit ec: ExecutionContext): EntityDecoder[IO, String] =
    EntityDecoder { msg =>
      DecodeResult.success(msg.body)
    }

  /** Body is parsed into JSON using JSON.parse(). */
  implicit def JSONDecoder(implicit ec: ExecutionContext): EntityDecoder[IO, js.Dynamic] =
    new EntityDecoder[IO, js.Dynamic] {
      def decode(msg: Message[IO]) =
        DecodeResult.success(msg.body.map(js.JSON.parse(_)))
    }

  /** Filter on JSON value. Create DecodeFailure if filter func returns false. */
  def JSONDecoderValidate(f: js.Dynamic => Boolean, failedMsg: String = "Failed validation.")(
      implicit ec: ExecutionContext): EntityDecoder[IO, js.Dynamic] = EntityDecoder { msg =>
    JSONDecoder.decode(msg).flatMap { v =>
      if (f(v)) EitherT.right(IO.pure(v))
      else EitherT.left(IO.pure(MessageBodyFailure(failedMsg)))
    }
  }

  /**
    * Decode the body as json and cast to A instead of JSONDecoder which casts
    * the body to js.Dynamic. Typebounuds implies that JS traits can use this
    * decoder easily.
    */
  def JsObjectDecoder[A <: js.Object](implicit ec: ExecutionContext): EntityDecoder[IO, A] =
    JSONDecoder.map(_.asInstanceOf[A])

  implicit def jsObjectDecoder[A <: js.Object](implicit ec: ExecutionContext) = JsObjectDecoder[A]

  /** Ignore the response completely (status and body) and return decode "unit" success. */
  implicit val void: EntityDecoder[IO, Unit] = EntityDecoder { _ =>
    DecodeResult.success(())
  }

  //import play.api.libs.json._

  // /** play-json */
  // implicit val PlayJsonDecoder: EntityDecoder[JsValue] =
  //   EntityDecoder.instance { msg =>
  //     DecodeResult.success(msg.body.map(Json.parse(_)))
  //   }

  /**
    * Check for value array and if there is a value array return the first
    * element.  Otherwise cast the entire response to A directly and return
    * it. Either way, return a single value of type `T`.
    *
    * If you are assuming a different underlying decode approach to the raw http
    * body, you need to write your own wrapper to detect the "value" array and
    * decide how to decode based on its presence. That's because its assumed in
    * this function that we will decode to a js.Object first to check for the
    * value array in the response body. This should really be called
    * `FirstElementOfValueArrayIfThereIsOneOrCastWholeMessage`.
    */
  def ValueWrapper[A <: js.Object](implicit ec: ExecutionContext) =
    JsObjectDecoder[ValueArrayResponse[A]].flatMapR[A] { arrresp =>
      // if no "value" array, assume its safe to cast to a single A
      arrresp.value.fold(DecodeResult.success[IO,A](arrresp.asInstanceOf[A]))({ arr =>
        if (arr.size > 0) DecodeResult.success(arr(0))
        else DecodeResult.failure(OnlyOneExpected(s"found ${arr.size}"))
      })
    }
}
