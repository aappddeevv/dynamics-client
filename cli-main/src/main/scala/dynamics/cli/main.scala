// Copyright (c) 2017 aappddeevv@gmail.com
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package cli

import scala.scalajs.js
import annotation._

object Main extends js.JSApp {

  def main(): Unit = {
    MainHelpers.run(AppConfig())
  }
}
