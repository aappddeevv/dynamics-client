// Copyright (c) 2017 aappddeevv@gmail.com
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package common

import scala.scalajs.js
import io.scalajs.npm.winston

class Logger() {
  import Logger._

  protected def front() = getDate() + ": "

  def debug(msg: String): Unit = if (atLeast(loglevel, "debug")) println(front() + "DEBUG: " + msg)
  def warn(msg: String): Unit  = if (atLeast(loglevel, "warn")) println(front() + "WARN: " + msg)
  def info(msg: String): Unit  = if (atLeast(loglevel, "info")) println(front() + "INFO: " + msg)
  def trace(msg: String): Unit = if (atLeast(loglevel, "info")) println(front() + "TRACE: " + msg)
  def error(msg: String): Unit = if (atLeast(loglevel, "info")) println(front() + "ERROR: " + msg)
}

object Logger {
  var loglevel: String = "error"

  protected val levels = Map[String, Int](
    "trace" -> 0,
    "debug" -> 1,
    "error" -> 2,
    "warn"  -> 3,
    "info"  -> 4
  )

  def atLeast(at: String, l: String): Boolean = {
    val atG = levels.get(at.toLowerCase()).getOrElse(100)
    val lG  = levels.get(l.toLowerCase()).getOrElse(-1);
    lG >= atG
  }

  def getDate() = s"[${js.Date()}]"
}

/** Technical logging. */
trait LazyLogger {
  //lazy val logger = winston.Winston
  lazy val logger = new Logger()
}

/** User messages logger. */
class UserLogger() {
  import UserLogger._
  def quiet(m: String)  = if (check(QUIET)) println(m)
  def normal(m: String) = if (check(NORMAL)) println(m)
  def verbose(m: String, l: Int = UserLogger.VERBOSE) =
    if (l >= level) println(m)
}

object UserLogger {
  val QUIET   = 0
  val NORMAL  = 1
  val VERBOSE = 2

  var level: Int = NORMAL

  def check(l: Int) = level >= l
}
