// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package client

import scala.scalajs.js
import scala.concurrent.{Future, ExecutionContext}
import js.annotation._
import fs2._
import cats._
import cats.data._
import cats.effect._
import js.JSConverters._

import dynamics.common._
import dynamics.http._

/**
 * @see https://developer.microsoft.com/en-us/graph/docs/concepts/errors
 */
@js.native
trait GraphErrorJS extends ODataErrorJS {
  val innererror: js.UndefOr[GraphInnerErrorJS] = js.undefined
}

@js.native
trait GraphInnerErrorJS extends js.Object {
}

case class GraphInnerError(stuff: js.Object)
case class GraphServerError(code: String, message: String, innererror: Option[GraphInnerError] = None) extends ServerError

object GraphServerError {

  /** Extract out a scala side error. Embedded newline chars are replaced with actual newlines. */
  def apply(err: GraphErrorJS): GraphServerError = {
    val ierror = err.innererror.map { i =>
      GraphInnerError(i)
    }.toOption

    GraphServerError(err.code.filterNot(_.isEmpty).getOrElse("<no code provided>"),
      err.message.getOrElse("<no message provided>"),
      ierror)
  }

  /** Typically for a JSON response, is there an error field? */
  @inline def hasError(obj: js.Object): Boolean = obj.hasOwnProperty("error")

  /** Typically for a an error object, is there an innererror field? */
  @inline def hasInnerErorr(obj: js.Object): Boolean = obj.hasOwnProperty("innererror")
}

/**
  * Combines a message, an optional DynamicsServerError, an optional underlying
  * error and the Status of the http call.
  */
sealed abstract class GraphError extends RuntimeException {
  def message: String
  final override def getMessage: String = message
  def cause: Option[GraphServerError]
  def underlying: Option[Throwable]
  def status: Status

  /** True if there was a server error. */
  def hasServerError = cause.isDefined
}

/**
  * Concrete implementation of errors thrown by a dynamics client.
  */
final case class GraphClientError(details: String,
                                     val cause: Option[GraphServerError] = None,
                                     underlying: Option[Throwable] = None,
                                     val status: Status)
    extends GraphError {
  //cause.foreach(initCause) // which one to use as underlying?
  def message = s"Graph client request encountered an error: $details"
}
