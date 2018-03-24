// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package http

import scala.collection.mutable

import dynamics.common._

/**
  * Unexpected status returned, the original request and response may be available.
  */
final case class UnexpectedStatus(status: Status,
                                  request: Option[HttpRequest] = None,
                                  response: Option[HttpResponse] = None)
    extends RuntimeException {
  override def toString(): String = {
    s"""UnexpectedStatus: status=$status${Option(status.reason).map("(" + _ + ")").getOrElse("")}, request=${request.toString()}, response=${response.toString()}"""
  }
}

/** Message failure in the http layer. */
sealed abstract class MessageFailure extends RuntimeException {
  def message: String
  final override def getMessage: String = message
}

/** Error for a Client to throw when something happens underneath it e.g. in the OS. */
final case class CommunicationsFailure(details: String, val cause: Option[Throwable] = None) extends MessageFailure {
  cause.foreach(initCause) // java's associate a throwable with this exception
  def message: String = s"Communications failure: $details"
}

/** Error for a Client when decoding of a returned message fails. */
sealed abstract class DecodeFailure extends MessageFailure {
  def cause: Option[Throwable]     = None
  override def getCause: Throwable = cause.orNull
}

final case class MessageBodyFailure(details: String, override val cause: Option[Throwable] = None)
    extends DecodeFailure {
  def message: String = s"Malformed body: $details"
}

final case class MissingExpectedHeader(details: String, override val cause: Option[Throwable] = None)
    extends DecodeFailure {
  def message: String = s"Expected header: $details"
}

final case class OnlyOneExpected(details: String, override val cause: Option[Throwable] = None) extends DecodeFailure {
  def message: String = s"Expected one: $details"
}
