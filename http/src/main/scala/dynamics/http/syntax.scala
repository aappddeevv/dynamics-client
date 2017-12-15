// Copyright (c) 2017 aappddeevv@gmail.com
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package http

import fs2._
import cats._
import cats.data._
import cats.implicits._
import cats.effect._

final case class DecodeResultOps[T](val dr: DecodeResult[T]) extends AnyVal {
  def toIO: IO[T] = dr.fold(IO.raiseError, IO.pure).flatten
}

trait DecodeResultSyntax {
  implicit def decodeResultOpsSyntax[T](dr: DecodeResult[T]) = DecodeResultOps(dr)
}

final case class EntityEncoderOps[A](a: A) extends AnyVal {
  def toEntity(implicit encoder: EntityEncoder[A]): (Entity, HttpHeaders) = encoder.encode(a)
}

trait EntityEncoderSyntax {
  implicit def entityEncoderOpsSyntax[A](a: A): EntityEncoderOps[A] = EntityEncoderOps[A](a)
}

case class MultipartOps(r: HttpRequest) {
  def toPart = SinglePart(r)
}

trait MultipartSyntax {
  implicit def httpRequestToOps(r: HttpRequest): MultipartOps = MultipartOps(r)
}

trait MultipartInstances {
  implicit val multipartEntityEncoder: EntityEncoder[Multipart] = new EntityEncoder[Multipart] {
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

trait AllInstances extends MultipartInstances with EntityEncoderInstances with EntityDecoderInstances

object instances {
  object all           extends AllInstances
  object entityEncoder extends EntityEncoderInstances with MultipartInstances
  object entityDecoder extends EntityDecoderInstances
}

object implicits extends AllSyntax with AllInstances
