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

@js.native
trait ODataErrorJS extends js.Object {
  val code: js.UndefOr[String]    = js.undefined
  val message: js.UndefOr[String] = js.undefined
}

/**
  * @see https://msdn.microsoft.com/en-us/library/mt770385.aspx,
  *  https://msdn.microsoft.com/en-us/library/gg334391.aspx#bkmk_parseErrors
  */
@js.native
trait DynamicsErrorJS extends ODataErrorJS {
  val innererror: js.UndefOr[DynamicsInnerErrorJS] = js.undefined
}

/**
  * Dynamics related inner error.
  * See https://msdn.microsoft.com/en-us/library/mt770385.aspx
  */
@js.native
trait DynamicsInnerErrorJS extends js.Object {
  @JSName("type")
  val etype: js.UndefOr[String]      = js.undefined
  val message: js.UndefOr[String]    = js.undefined
  val stacktrace: js.UndefOr[String] = js.undefined
}

/** Scala version of ODataErrorJS error. */
trait ServerError {
  def code: String
  def message: String
}

/** Scala version of inner DynamicsInnerErrorJS */
case class InnerError(etype: String, message: String, stacktrace: String)
/** Scala version of a dynamics server error. */ 
case class DynamicsServerError(code: String, message: String, innererror: Option[InnerError] = None) extends ServerError

object DynamicsServerError {

  /** Extract out a scala side error. Embedded newline chars are replaced with actual newlines. */
  def apply(err: DynamicsErrorJS): DynamicsServerError = {
    val ierror = err.innererror.map { i =>
      InnerError(
        i.etype.getOrElse("<no error type provided>"),
        i.message.getOrElse("<no message provided>"),
        i.stacktrace.map(_.replaceAll("\\r\\n", "\n")).getOrElse("<no stacktrace provided>")
      )
    }.toOption

    DynamicsServerError(err.code.filterNot(_.isEmpty).getOrElse("<no code provided>"),
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
sealed abstract class DynamicsError extends RuntimeException {
  def message: String
  final override def getMessage: String = message
  def cause: Option[DynamicsServerError]
  def underlying: Option[Throwable]
  def status: Status

  /** True if there was a server error. */
  def hasServerError = cause.isDefined
}

/**
  * Concrete implementation of errors thrown by a dynamics client.
  */
final case class DynamicsClientError(details: String,
                                     val cause: Option[DynamicsServerError] = None,
                                     underlying: Option[Throwable] = None,
                                     val status: Status)
    extends DynamicsError {
  //cause.foreach(initCause) // which one to use as underlying?
  def message = s"Dynamics client request encountered an error: $details"
}
