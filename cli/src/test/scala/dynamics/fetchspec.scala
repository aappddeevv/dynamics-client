package dynamics

import org.scalatest._
import cats._
import cats.data._
import cats.implicits._
import cats.free.Free._
//import simulacrum.typeclass
import scala.concurrent._
import duration._
import java.util.concurrent.{TimeUnit => TU}
import scalajs.js._

//import ODataIO._

/*
object interpreters {

  /** Capture effects.
    */
  @typeclass trait Capture[M[_]] {
    def apply[A](a: => A): M[A]
    def delay[A](a: => A): M[A] = apply(a)
  }

  class Interpreters[M[_]: Capture] {

    def FetchInterpreter: ODataOp ~> Id = new (ODataOp ~> Id) {
      def apply[A](fa: ODataOp[A]): Id[A] =
        fa match {
          case Execute(url, opts) => println(s"execute: $url"); null
          case CheckOk(checker) => println("checking response"); None
          case GetHeader(name) => println(s"find header: $name"); "header-value"
          case ToJson() =>
            println("convert to json"); scalajs.js.Dynamic.literal("result" -> "blah")
        }
    }
    def interpreter = FetchInterpreter
  }

  implicit val idCaptureInstance = new Capture[Id] {
    override def apply[A](a: => A): Id[A] = a
  }

}

import interpreters._
import fs2helpers._
import dynamics.Implicits._
 */

class FetchSpec extends AsyncFlatSpec
    with Matchers with OptionValues {

  import fs2._
  import fs2.util._
  import scala.concurrent.duration._

  override implicit val executionContext = scala.concurrent.ExecutionContext.global
  implicit val s: Strategy = Strategy.fromExecutionContext(executionContext)
  implicit val scheduler: Scheduler = Scheduler.default
  implicit val F = Async[Task]

  def init() = {
    val info = Utils.slurpAsJson[ConnectionInfo]("dynamics.json")
    val authority = info.authorityHostUrl map (_ + "/" + info.tenant)
    val ctx = new AuthenticationContext(authority.get, true)
    (info, ctx)
  }

  /*
  val interpreter = new Interpreters[Id].interpreter

  "fetch free monad" should "execute a fetch" in {
    val empty = scalajs.js.Dynamic.literal()

    def fetchit(url: String): ODataIO[scalajs.js.Dynamic] =
      for {
        _ <- execute(url, EmptyClientOpts)
        _ <- check2XX
        json <- toJson()
      } yield json

    val result = fetchit("/publishers") foldMap interpreter
    Future { result.result shouldBe "blah" }
  }
 */

  "authentication context" should "be created from config ConnectionInfo" in {
    val (info, ctx) = init()
    require(
      info.username.isDefined &&
        info.password.isDefined &&
        info.applicationId.isDefined &&
        info.acquireTokenResource.isDefined)

    val tokenF = ADALHelpers.acquireTokenWithUsernamePassword(ctx,
      info.acquireTokenResource.get,
      info.username.get,
      info.password.get,
      info.applicationId.get)

    tokenF.unsafeRunAsyncFuture() map { ti =>

      def parseDate(image: String) = scalajs.js.Date.parse(image)

      println(Utils.pprint(ti))
      //val expiresOn = parseDate(ti.expiresOn)
      println(s"Expires on: ${ti.expiresOn}")
      val current = scalajs.js.Date.now()
      val delta = ti.expiresOn.getTime() - current
      val deltaFrac = 0.9 * delta
      println(s"Delta millis: $deltaFrac")

      assert(ti.accessToken != null)
    }
  }

  /*
  it should "run a continuous auth stream" in {
    val (info, ctx) = init()
    val tokenF = ADALHelpers.acquireTokenWithUsernamePassword(ctx,
      info.acquireTokenResource.get,
      info.username.get,
      info.password.get,
      info.applicationId.get)
    val f = tokenF.attempt.map(_.toOption)
    val authStream = unfoldEvalWithDelay[TokenInfo](f,
      (ti: TokenInfo) => {
        println("shortening request to 2 seconds")
        shortenDelay(delay=FiniteDuration(2 /*ti.expiresIn*/, TU.SECONDS))
      })
    authStream.take(5).runLog.unsafeRunAsyncFuture.map { vec =>
      vec.length shouldBe 5
    }
  }

  "client" should "connect for a simple query" in {
    val (info, ctx) = init()
    val client = Client.forNodeFetch(info)

    val request = new HttpRequest("/publishers")
    val items = client.fetch(request)(r =>
      r.json().toTask.map(_.value.asInstanceOf[Array[scalajs.js.Dynamic]]))

    items.unsafeRunAsyncFuture().map(items => assert(items.length > 0))
  }
   */

}
