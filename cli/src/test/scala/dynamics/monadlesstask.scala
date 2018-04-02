// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics

import org.scalatest._
import MonadlessTask._
import fs2._

class MonadlessTaskSpec extends FlatSpec with Matchers with OptionValues {

  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val s = Strategy.fromExecutionContext(global)

  "monadless task" should "work" in {
    val x = lift {
      val a = unlift(Task.now(1))
      val b = unlift(Task.now(2))
      val c = unlift(Task.delay(3))
      a + b + c
    }
    x.unsafeValue.value should be(6)
  }
}
