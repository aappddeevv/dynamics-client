// Copyright (c) 2017 aappddeevv@gmail.com
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package http

import fs2._
import cats._
import cats.data._
import cats.implicits._
import fs2.interop.cats._

final case class DecodeResultOps[T](val dr: DecodeResult[T]) extends AnyVal {
  def toTask: Task[T] = dr.fold(Task.fail, Task.now).flatten
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

trait EntityEncoderImplicits {
  implicit def aToEntity[A](a: A)(implicit e: EntityEncoder[A]): (Entity, HttpHeaders) = EntityEncoderOps(a).toEntity
}

trait MultipartSyntax {
  def toPart(r: HttpRequest) = SinglePart(r)
}

trait MultipartImplicits {
  implicit def httpRequestToPart(r: HttpRequest): SinglePart = SinglePart(r)
}

trait AllSyntax extends MultipartSyntax with EntityEncoderSyntax with DecodeResultSyntax

object syntax {
  object all           extends AllSyntax
  object multipart     extends MultipartSyntax
  object entityencoder extends EntityEncoderSyntax
  object decoderesult  extends DecodeResultSyntax
}

trait AllImplicits extends MultipartImplicits with EntityEncoderImplicits

object implicits extends AllImplicits with AllSyntax
