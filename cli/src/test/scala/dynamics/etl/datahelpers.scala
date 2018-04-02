// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package etl

import scala.scalajs.js
import js._

import org.scalatest._
import etl.jsdatahelpers._

class DataHelpersSpec extends FlatSpec with Matchers with OptionValues {

  import dynamics.syntax.jsdynamic._

  "rename" should "rename attributes" in  {
    val rens = Seq("blah", "hah")
    val obj = js.Dynamic.literal("blah" -> "hah").asDict[js.Any]
    rename(obj, ("blah" -> "hah"))
    obj.get("hah").value shouldBe("hah")
  }

  "keep" should "keep attributes" in {
    val keeps = Seq("blah")
    val obj = js.Dynamic.literal("hah" -> 1, "blah" -> 10).asDict[js.Any]
    keepOnly(obj, keeps:_*)
    assert(obj.get("hah") == None)
    obj.get("blah").value shouldBe (10)
  }

  "omit" should "omit attributes" in {
    val omits = Seq("blah")
    val obj = js.Dynamic.literal("hah" -> 1, "blah" -> 10).asDict[js.Any]
    omit(obj, omits:_*)
    assert(obj.get("hah") == Some(1))
    obj.get("blah") shouldBe (None)
  }

}
