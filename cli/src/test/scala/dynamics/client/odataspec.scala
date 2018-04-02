// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package client

import org.scalatest._

import OData._
import dynamics.client.implicits._

class ODataSpec extends FlatSpec with Matchers with OptionValues {

  "prefer" should "render maxpagesize" in {
    val popts = PreferOptions(Some(10), None, None, None)
    val result = OData.render(popts)
    result shouldBe ("""odata.maxpagesize=10,odata.include-annotations="*"""")
  }

  it should "render *" in {
    val popts = PreferOptions()
    val result = OData.render(popts)
    result shouldBe ("""odata.include-annotations="*"""")
  }

  it should "render a single option when true" in {
    val popts = PreferOptions(includeFormattedValues = Some(true))
    val result = OData.render(popts)
    result shouldBe ("""odata.include-annotations="OData.Community.Display.V1.FormattedValue"""")
  }

  it should "not render a single option when false" in {
    val popts = PreferOptions(includeFormattedValues = Some(false))
    val result = OData.render(popts)
    result shouldBe ("""odata.include-annotations="*"""")
  }

}

class MultipartSpec extends AsyncFlatSpec with Matchers with OptionValues {

  def contentId(i: Int) = HttpHeaders("Content-ID" -> i.toString())

  "Render" should "render" in {
    val req = HttpRequest(Method.GET, "http://get1", body = Entity.fromString("{}"))
    val req2 = HttpRequest(Method.GET, "http://get2", body = Entity.fromString("{}"))
    val p = Multipart(Seq(
      SinglePart(req),
      ChangeSet(Seq(
        SinglePart(HttpRequest(Method.GET, "http://put1", body=Entity.fromString("{}"),
          headers = HttpHeaders("Content-Type" -> "crazy")), contentId(666)),
        HttpRequest(Method.GET, "http://put2", body=Entity.fromString("{}"))
      ), Boundary("YYY")),
      SinglePart(req2)),
      Boundary("XXX"))
    val (r, h) = EntityEncoder[Multipart].encode(p)
    r.map { content =>
      println(s"headers: $h")
      println(s"rendered content: $content")
      succeed
    }.unsafeRunAsyncFuture()
  }


}
