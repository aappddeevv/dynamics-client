// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package http

import scala.scalajs.js
import js.{|, _}
import scala.concurrent.{Future, ExecutionContext}
import js.annotation._
import fs2._
import cats._
import cats.data._
import cats.implicits._
import cats.effect._
import js.JSConverters._
import scala.annotation.implicitNotFound
import org.slf4j._

import dynamics.common._
import fs2helpers._
import dynamics.http.implicits._
import dynamics.common.implicits._

/*
object StreamingClient {

  import dynamics.Implicits._
  import NodeFetch._

  def forNodeFetch(baseURL: String, debug: Boolean = false)
    (implicit ec: ExecutionContext, strategy: Strategy, scheduler: Scheduler): StreamingClient[DisposableResponse] = {
    val url =
      if (baseURL.endsWith("/")) baseURL.dropRight(1)
      else baseURL

    val svc = (request: HttpRequest) => {
      val hashttp = request.path.startsWith("http")
      assert(request.path(0) == '/' || hashttp,
        s"Request path must start with a slash (/) or http: ${request.path}}")
      val h = OData.getBasicHeaders()
      if(debug) {
        println(s"FETCH URL: ${request.path}")
        println(s"FETCH OPTS: ${request.opts.show}")
      }
      val mergedHeaders = request.headers ++ h // mergeOpts(ClientOpts("headers" -> h), request.opts)
      fetch((if(!hashttp) url else "") + request.path, merged.toJSDictionary).toTask
        .map(r => DisposableResponse(HttpResponse(r, Status.lookup(r.status)), Task.now(())))
    }
    pipe.lift(svc)
  }
}
 */

object NodeFetch {

  @js.native
  @JSImport("node-fetch", "Headers")
  class Headers(init: Headers = js.undefined.asInstanceOf[Headers]) extends js.Object {
    //extends js.Iterable[Array[js.Any]] {
    def append(k: String, v: String): Unit = js.native
    def get(k: String): String             = js.native // can be null
    def get(): js.Array[String]            = js.native
    def set(k: String, v: js.Any): Unit    = js.native
    def has(k: String): Boolean            = js.native
    def delete(k: String): Unit            = js.native

    @JSName("raw")
    def raw2(): js.Dictionary[js.Any] = js.native
    /*
     @JSName(js.Symbol.iterator)
     def jsIterator(): Iterator[Array[js.Any]] = js.native // each item is [k, v]
     */
    def keys(): Iterator[String] = js.native
    /*
     def values(): Iterator[js.Any]            = js.native
     */
    def forEach(cb: js.Function2[js.Any, String, Unit]): Unit = js.native

    /** Access fetch speciic property. */
    def raw(): js.Dictionary[js.Array[String]] = js.native
  }

  object Headers {
    import js.JSConverters._
    implicit class ShowHeaders(h: Headers) {
      def show = {
        val sb = new StringBuilder()
        sb.append("Headers:\n")
        h.forEach((v: js.Any, k: String) => sb.append(s"$k = $v\n"))
        sb.toString
      }
    }
  }

  def merge(target: Headers, sources: Headers*): Unit =
    sources foreach {
      _.forEach { (v: js.Any, k: String) =>
        target.set(k, v)
      }
    }

  import Headers._

  @js.native
  @JSImport("node-fetch", "Response")
  class Response extends js.Object {
    val status: Int                    = js.native
    val statusText: String             = js.native
    def text(): js.Promise[String]     = js.native
    def json(): js.Promise[js.Dynamic] = js.native
    val headers: Headers               = js.native
    /*
    val bodyUsed: Boolean = js.native
    val body: js.Any = js.native
    val size: Int = js.native
    val _raw: js.Array[js.Any] = js.native
    val _abort: Boolean = js.native
   */
  }

  /*
  object Response {
    def show(r: Response) = {
      val sb = new StringBuilder()
      sb.append("Response:\n")
      sb.append("status=" + r.status + ", statusText=" + r.statusText)
      sb.append("\n")
      sb.append(r.headers.show)
      sb.toString
    }
  }
   */
  @js.native
  @JSImport("node-fetch", JSImport.Namespace)
  object fetch extends js.Object {
    def apply(url: String): js.Promise[Response]                          = js.native
    def apply(url: String, options: RequestOptions): js.Promise[Response] = js.native
  }

  class RequestOptions(val agent: js.UndefOr[String] = js.undefined,
                       val body: js.Any = js.undefined,
                       val compress: js.UndefOr[Boolean] = js.undefined,
                       val headers: js.UndefOr[js.Any] = js.undefined,
                       val method: js.UndefOr[String] = js.undefined,
                       val timeout: js.UndefOr[Int] = js.undefined)
      extends js.Object
}

case class NodeFetchClientOptions(
    timeoutInMillis: Int = 0, // wait as long as needed
    compress: Boolean = true
)

object NodeFetchClient extends LazyLogger {
  import NodeFetch._

  val DefaultNodeFetchClientOptions = NodeFetchClientOptions()

  /**
    * Create Client from connection information.
    *
    * @param info Connection information
    * @param debug Temporary parameter to turn debugging on or off in this client.
    * @param defaultHeaders Headers applied to every request.
    */
  def create[F[_]](info: ConnectionInfo,
                   debug: Boolean = false,
                   defaultHeaders: HttpHeaders = HttpHeaders.empty,
                   options: NodeFetchClientOptions = DefaultNodeFetchClientOptions)(
      implicit ec: ExecutionContext,
      F: MonadError[F, Throwable],
      scheduler: Scheduler,
      PtoF: js.Promise ~> F,
      IOtoF: IO ~> F,
      PtoIO: js.Promise ~> IO): Client[F] = {

    require(info.dataUrl.isDefined)
    val donothing = F.pure(())

    val base =
      if (info.dataUrl.get.endsWith("/")) info.dataUrl.get.dropRight(1)
      else info.dataUrl.get

    val svc: Kleisli[F, HttpRequest[F], DisposableResponse[F]] = Kleisli { request =>
      val hashttp = request.path.startsWith("http")
      assert(request.path(0) == '/' || hashttp, s"Request path must start with a slash (/) or http: ${request.path}}")
      val mergedHeaders: HttpHeaders = defaultHeaders ++ request.headers
      val url                        = (if (!hashttp) base else "") + request.path
      // using body string, call the fetch
      IOtoF(request.body).flatMap { bodyString =>
        if (debug) {
          logger.debug(s"FETCH URL: $url")
          logger.debug(s"FETCH HTTP REQUEST INPUT: $request")
          logger.debug(s"FETCH BODY: ${bodyString}")
          logger.debug("FETCH FINAL HEADERS: " + HttpHeaders.render(mergedHeaders))
        }
        val fetchopts = new RequestOptions(
          body = bodyString,
          headers = mergedHeaders.mapValues(_.mkString(";")).toJSDictionary,
          compress = options.compress,
          timeout = options.timeoutInMillis,
          method = request.method.name
        )
        PtoF(fetch(url, fetchopts)).attempt.flatMap {
          case Right(r) =>
            // convert headers as String -> Seq[String] to just String -> String, is this wrong?
            val headers: HttpHeaders = r.headers.raw().mapValues(_.toSeq).toMap
            //.asInstanceOf[js.Dictionary[Seq[String]]]
            val hresp = HttpResponse[F](Status.lookup(r.status), headers, PtoIO(r.text()))
            val dr    = DisposableResponse(hresp, donothing)
            if (debug) {
              logger.debug(s"FETCH RESPONSE: $dr")
              //println(s"Raw headers: ${Utils.pprint(r.headers.raw2().asInstanceOf[js.Dynamic])}")
            }
            F.pure(dr)
          case Left(e) =>
            logger.error(s"node-fetch failure: $e")
            F.raiseError(CommunicationsFailure("node fetch client", Some(e)))
        }
      }
    }
    Client(svc, donothing)
  }
}
