// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package client
package common

import scala.scalajs.js
import js._
import cats.effect._
import fs2._

import dynamics.common._
import dynamics.http._
import fs2helpers._


/**
 * Methods common to clients. Many take alot of parameters in order to allow
 * them to be used in multiple places.
 */
trait ClientMethods extends LazyLogger {

  def http: Client[IO]
  def responseToFailedTask[A](resp: HttpResponse[IO], msg: String, req: Option[HttpRequest[IO]]): IO[A]

  /**
    * Get a list of values. Follows @data.nextLink but accumulates all the
    * results into memory. Prefer [[getListStream]]. For now, the caller must
    * decode external to this method. The url is usually generated from a
    * QuerySpec.
    */
  protected def _getList[A <: js.Any](url: String, headers: HttpHeaders)(): IO[Seq[A]] =
    _getListStream[A](url, headers).compile.toVector

  /**
    * Get a list of values as a stream. Follows @odata.nextLink. For now, the
    * caller must decode external to this method.
    */
  protected def _getListStream[A <: js.Any](url: String, headers: HttpHeaders): Stream[IO, A] = {
    val str: Stream[IO, Seq[A]] = Stream.unfoldEval(Option(url)) {
      _ match {
        // Return a IO[Option[(Seq[A],Option[String])]]
        case None => IO.pure(None)
        case Some(nextLink) =>
          val request = HttpRequest[IO](Method.GET, nextLink, headers = headers)
          http.fetch(request) {
            case Status.Successful(resp) =>
              resp.body.map { str =>
                val odata = js.JSON.parse(str).asInstanceOf[ValueArrayResponse[A]]
                if (logger.isDebugEnabled())
                  logger.debug(s"getListStream: body=$str\nodata=${PrettyJson.render(odata)}")
                val a = odata.value.map(_.toSeq) getOrElse Seq()
                //println(s"getList: a=$a,\n${PrettyJson.render(a(0).asInstanceOf[js.Object])}")
                Option((a, odata.nextLink.toOption))
              }
            case failedResponse =>
              responseToFailedTask(failedResponse, s"getListStream $url", Option(request))
          }
      }
    }
    // Flatten the seq chunks from each unfold iteration
    str.flatMap(Stream.emits[A])
  }
}
