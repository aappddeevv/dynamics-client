// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics

import org.scalatest._

import scala.concurrent.duration._
import java.util.concurrent.{TimeUnit => TU}

class MiscSpec extends FlatSpec with Matchers with OptionValues {

  import Utils._

  "filterForMatches" should "select everything when no filter" in {
    val result = filterForMatches(Seq((1, Seq("blah", "hah"))))
    result should contain(1)
  }

  it should "select on exact regex" in {
    val result = filterForMatches(Seq((1, Seq("blah", "hah"))), Seq("hah"))
    result should contain(1)
  }

  it should "not select if regex does not match" in {
    val result = filterForMatches(Seq((1, Seq("blah", "hah"))), Seq("xhah"))
    result.length shouldBe (0)
  }

  "getPostfix" should "get the postfix" in {
    getPostfix("blah.hah").value shouldBe ("hah")
  }

  "inferWebResourceType" should "identify a jpg" in {
    getPostfix("blah.jpg").flatMap(inferWebResourceType(_)).value shouldBe (6)
  }

  "splitUpTo" should "split a path" in {
    stripUpTo("/blah/new_/hah.js", "new_") shouldBe ("new_/hah.js")
  }

  it should "find a filename with prefix" in {
    stripUpTo("/blah/new_hah.js", "new_") shouldBe ("new_hah.js")
  }

  it should "find a filename with prefix but no path" in {
    stripUpTo("new_hah.js", "new_") shouldBe ("new_hah.js")
  }

  it should "return the same string if the prefix is blank" in {
    stripUpTo("new_hah.js", "") shouldBe ("new_hah.js")
  }

  "finiteduration" should "actually work" in {
    val fd  = FiniteDuration(5, TU.SECONDS)
    val fdp = (fd * 3.0 / 5.0).toSeconds
    val fd2 = fs2helpers.shortenDelay(delay = FiniteDuration(10, TU.SECONDS), fraction = 0.5)
    val fd3 = fs2helpers.shortenDelay(delay = FiniteDuration(100, TU.SECONDS), fraction = 0.10)
    val fd4 = fs2helpers.shortenDelay(delay = FiniteDuration(3600, TU.SECONDS))
    assert(fd4.toSeconds == (0.95 * 3600).toInt)
    assert(fd3.toSeconds == 10)
    assert(fd2.toSeconds == 5)
    assert(fdp == 3)
    fd.toSeconds shouldBe (5)
  }

}
