// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package http

import fs2._
import cats._
import cats.data._
import cats.implicits._
import cats.effect._

final case class DecodeResultOps[F[_], T](val dr: DecodeResult[F, T])(implicit F: MonadError[F, Throwable]) {

  /** Flatten `EitherT[F,DecodeFailure,T]`. Push any DecodeFailure as the error into a `F[T]`. */
  def toF: F[T] = F.flatten(dr.fold(F.raiseError, F.pure))
}

trait DecodeResultSyntax {
  implicit def decodeResultOpsSyntax[F[_], T](dr: DecodeResult[F, T])(implicit F: MonadError[F, Throwable]) =
    DecodeResultOps(dr)
}

final case class EntityEncoderOps[A](a: A) extends AnyVal {
  def toEntity(implicit encoder: EntityEncoder[A]): (Entity, HttpHeaders) = encoder.encode(a)
}

trait EntityEncoderSyntax {
  implicit def entityEncoderOpsSyntax[A](a: A): EntityEncoderOps[A] = EntityEncoderOps[A](a)
}

final case class MultipartOps[F[_]](r: HttpRequest[F]) {
  def toPart = SinglePart(r)
}

trait MultipartSyntax {
  implicit def httpRequestToOps[F[_]](r: HttpRequest[F]): MultipartOps[F] = MultipartOps(r)
}

trait MultipartInstances {
  implicit def multipartEntityEncoder: EntityEncoder[Multipart] =
    new EntityEncoder[Multipart] {
      def encode(m: Multipart) =
        (Multipart.render(m),
         HttpHeaders.empty ++ Map("Content-Type" -> Seq(Multipart.MediaType, "boundary=" + m.boundary.value)))
    }
}

trait AllSyntax extends MultipartSyntax with EntityEncoderSyntax with DecodeResultSyntax

object syntax {
  object all           extends AllSyntax
  object multipart     extends MultipartSyntax
  object entityencoder extends EntityEncoderSyntax
  object decoderesult  extends DecodeResultSyntax
}

trait AllInstances
    extends MultipartInstances
    with EntityEncoderInstances
    with EntityDecoderInstances
    with MethodInstances

object instances {
  object all           extends AllInstances
  object entityencoder extends EntityEncoderInstances with MultipartInstances
  object entitydecoder extends EntityDecoderInstances
  object method        extends MethodInstances
}

object implicits extends AllSyntax with AllInstances
