// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package client

import org.scalatest._
import cats._
import cats.data._
import cats.implicits._
import fs2.interop.cats._
import scala.concurrent._
import duration._
import java.util.concurrent.{TimeUnit => TU}
import scalajs.js._
import fs2._
import scala.concurrent.duration._

case class TestMessage(val headers: HttpHeaders, val body: Entity) extends Message

class DecoderSpec extends AsyncFlatSpec with Matchers with OptionValues {

  override implicit val executionContext = scala.concurrent.ExecutionContext.global
  implicit val s: Strategy = Strategy.fromExecutionContext(executionContext)
  implicit val scheduler: Scheduler = Scheduler.default

  "EntityBody" should "render to  a string" in {
    val msg = TestMessage(HttpHeaders.empty, Entity.fromString("{}"))
    msg.body.map { _ shouldBe("{}")}.unsafeRunAsyncFuture
  }

  "EntityDecoder" should "decode a string body" in {
    val decoder = EntityDecoder.TextDecoder
    val msg = TestMessage(HttpHeaders.empty, Entity.fromString("{}"))
    decoder.decode(msg).fold(_ => fail, _ shouldBe("{}")).unsafeRunAsyncFuture
  }

  it should "flatmap" in {
    val decoder = EntityDecoder.TextDecoder
    val msg = TestMessage(HttpHeaders.empty, Entity.fromString("{}"))
    decoder.flatMapR(v => DecodeResult.success(v + "!")).decode(msg).
      fold(_ => fail, _ shouldBe("{}!")).unsafeRunAsyncFuture
  }

  it should "orElse" in {
    //val decoderGood =
    //  EntityDecoders.JSONDecoderValidate(jval => !scalajs.js.isUndefined(jval.found))
    val decoder = EntityDecoder.JSONDecoderValidate(jval => !scalajs.js.isUndefined(jval.found)).
      map(_.found.asInstanceOf[Int])
    val value = """{ "found": 1 }"""
    val msg = TestMessage(HttpHeaders.empty, Entity.fromString(value))
    decoder.decode(msg).fold(_ => fail, _ shouldBe(1)).unsafeRunAsyncFuture
  }

}
