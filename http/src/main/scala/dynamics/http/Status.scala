// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package http

import cats._
import cats.data._
import cats.implicits._

/** Represents a Status. Allows us to manage status
  * values in web calls more easily.
  * Provide a way to match a response to a status or other criteria
  * in a DSL-sort of way.
  * {{{
  * client.fetch(...) {
  *  case Status(200)(response) => decode response
  *  case failedResponse => Task.fail(...)
  * }
  * }}}
  * Copied from http4s.
  */
final case class Status(val code: Int)(val reason: String) extends Ordered[Status] {
  import Status._

  val responseClass: ResponseClass =
    if (code < 200) Status.Informational
    else if (code < 300) Status.Successful
    else if (code < 400) Status.Redirection
    else if (code < 500) Status.ClientError
    else Status.ServerError

  def compare(that: Status): Int = code - that.code

  def isSuccess: Boolean = responseClass.isSuccess

  def withReason(reason: String): Status = new Status(code)(reason)

  def unapply[F[_]](msg: HttpResponse[F]): Option[HttpResponse[F]] = {
    if (msg.status == this) Some(msg) else None
  }
}

object Status {

  implicit val statusShow: Show[Status] = Show { s =>
    s"${s.code}: (${s.reason})"
  }

  sealed trait ResponseClass {
    def isSuccess: Boolean

    /** Match a [[Response]] based on [[Status]] category */
    final def unapply[F[_]](resp: HttpResponse[F]): Option[HttpResponse[F]] =
      if (resp.status.responseClass == this) Some(resp) else None
  }

  case object Informational extends ResponseClass { val isSuccess = true  }
  case object Successful    extends ResponseClass { val isSuccess = true  }
  case object Redirection   extends ResponseClass { val isSuccess = true  }
  case object ClientError   extends ResponseClass { val isSuccess = false }
  case object ServerError   extends ResponseClass { val isSuccess = false }

  def lookup(code: Int): Status =
    StatusReasons.get(code).getOrElse(Status(code)("No description avaliable."))

  private val StatusReasons: Map[Int, Status] = Map(
    200 -> Status(200)("OK"),
    201 -> Status(201)("Created"),
    202 -> Status(202)("Accepted"),
    203 -> Status(203)("Non-Authoritative Information"),
    204 -> Status(204)("No Content"),
    205 -> Status(205)("Reset Content"),
    206 -> Status(206)("Partial Content"),
    207 -> Status(207)("Multi-Status"),
    208 -> Status(208)("Already Reported"),
    226 -> Status(226)("IM Used"),
    300 -> Status(300)("Multiple Choices"),
    301 -> Status(301)("Moved Permanently"),
    302 -> Status(302)("Found"),
    303 -> Status(303)("See Other"),
    304 -> Status(304)("Not Modified"),
    305 -> Status(305)("Use Proxy"),
    307 -> Status(307)("Temporary Redirect"),
    308 -> Status(308)("Permanent Redirect"),
    400 -> Status(400)("Bad Request"),
    401 -> Status(401)("Unauthorized"),
    402 -> Status(402)("Payment Required"),
    403 -> Status(403)("Forbidden"),
    404 -> Status(404)("Not Found"),
    405 -> Status(405)("Method Not Allowed"),
    406 -> Status(406)("Not Acceptable"),
    407 -> Status(407)("Proxy Authentication Required"),
    408 -> Status(408)("Request Timeout"),
    409 -> Status(409)("Conflict"),
    410 -> Status(410)("Gone"),
    411 -> Status(411)("Length Required"),
    412 -> Status(412)("Precondition Failed"),
    413 -> Status(413)("Payload Too Large"),
    414 -> Status(414)("URI Too Long"),
    415 -> Status(415)("Unsupported Media Type"),
    416 -> Status(416)("Range Not Satisfiable"),
    417 -> Status(417)("Expectation Failed"),
    422 -> Status(422)("Unprocessable Entity"),
    423 -> Status(423)("Locked"),
    424 -> Status(424)("Failed Dependency"),
    426 -> Status(426)("Upgrade Required"),
    428 -> Status(428)("Precondition Required"),
    429 -> Status(429)("Too Many Requests"),
    431 -> Status(431)("Request Header Fields Too Large"),
    451 -> Status(451)("Unavailable For Legal Reasons"),
    500 -> Status(500)("Internal Server Error"),
    501 -> Status(501)("Not Implemented"),
    502 -> Status(502)("Bad Gateway"),
    503 -> Status(503)("Service Unavailable"),
    504 -> Status(504)("Gateway Timeout"),
    505 -> Status(505)("HTTP Version not supported"),
    506 -> Status(506)("Variant Also Negotiates"),
    507 -> Status(507)("Insufficient Storage"),
    508 -> Status(508)("Loop Detected"),
    510 -> Status(510)("Not Extended"),
    511 -> Status(511)("Network Authentication Required")
  )

  val OK                  = StatusReasons(200)
  val Created             = StatusReasons(201)
  val Accepted            = StatusReasons(202)
  val NoContent           = StatusReasons(204)
  val BadRequest          = StatusReasons(400)
  val Unauthorized        = StatusReasons(401)
  val NotFound            = StatusReasons(404)
  val RequestTimeout      = StatusReasons(408)
  val PreconditionFailed  = StatusReasons(412)
  val TooManyRequests     = StatusReasons(429)
  val InternalServerError = StatusReasons(500)
  val BadGateway          = StatusReasons(502)
  val ServiceUnavailable  = StatusReasons(503)
  val GatewayTimeout      = StatusReasons(504)
}
