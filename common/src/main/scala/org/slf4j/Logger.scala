// Copyright (c) 2017 aappddeevv@gmail.com
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package org.slf4j

trait Logger {
  def getName(): String
  def factory: LoggerFactory
  def isTraceEnabled(): Boolean = factory.levelEnableFor(getName(), "trace")
  def trace(msg: String): Unit
  def trace(format: String, arg: Object): Unit
  def trace(format: String, arg1: Object, arg2: Object): Unit
  def trace(format: String, arguments: Object*): Unit
  def trace(msg: String, t: Throwable): Unit
  def isTraceEnabled(marker: Marker): Boolean = factory.levelEnableFor(getName(), "trace")
  def trace(marker: Marker, msg: String): Unit
  def trace(marker: Marker, format: String, arg: Object): Unit
  def trace(marker: Marker, format: String, arg1: Object, arg2: Object): Unit
  def trace(marker: Marker, format: String, argArray: Object*): Unit
  def trace(marker: Marker, msg: String, t: Throwable): Unit
  def isDebugEnabled(): Boolean = factory.levelEnableFor(getName(), "trace")
  def debug(msg: String): Unit
  def debug(format: String, arg: Object): Unit
  def debug(format: String, arg1: Object, arg2: Object): Unit
  def debug(format: String, arguments: Object*): Unit
  def debug(msg: String, t: Throwable): Unit
  def isDebugEnabled(marker: Marker): Boolean = factory.levelEnableFor(getName(), "trace")
  def debug(marker: Marker, msg: String): Unit
  def debug(marker: Marker, format: String, arg: Object): Unit
  def debug(marker: Marker, format: String, arg1: Object, arg2: Object): Unit
  def debug(marker: Marker, format: String, arguments: Object*): Unit
  def debug(marker: Marker, msg: String, t: Throwable): Unit
  def isInfoEnabled(): Boolean = factory.levelEnableFor(getName(), "trace")
  def info(msg: String): Unit
  def info(format: String, arg: Object): Unit
  def info(format: String, arg1: Object, arg2: Object): Unit
  def info(format: String, arguments: Object*): Unit
  def info(msg: String, t: Throwable): Unit
  def isInfoEnabled(marker: Marker): Boolean = factory.levelEnableFor(getName(), "trace")
  def info(marker: Marker, msg: String): Unit
  def info(marker: Marker, format: String, arg: Object): Unit
  def info(marker: Marker, format: String, arg1: Object, arg2: Object): Unit
  def info(marker: Marker, format: String, arguments: Object*): Unit
  def info(marker: Marker, msg: String, t: Throwable): Unit
  def isWarnEnabled(): Boolean = factory.levelEnableFor(getName(), "trace")
  def warn(msg: String): Unit
  def warn(format: String, arg: Object): Unit
  def warn(format: String, arguments: Object*): Unit
  def warn(format: String, arg1: Object, arg2: Object): Unit
  def warn(msg: String, t: Throwable): Unit
  def isWarnEnabled(marker: Marker): Boolean = factory.levelEnableFor(getName(), "trace")
  def warn(marker: Marker, msg: String): Unit
  def warn(marker: Marker, format: String, arg: Object): Unit
  def warn(marker: Marker, format: String, arg1: Object, arg2: Object): Unit
  def warn(marker: Marker, format: String, arguments: Object*): Unit
  def warn(marker: Marker, msg: String, t: Throwable): Unit
  def isErrorEnabled(): Boolean = factory.levelEnableFor(getName(), "trace")
  def error(msg: String): Unit
  def error(format: String, arg: Object): Unit
  def error(format: String, arg1: Object, arg2: Object): Unit
  def error(format: String, arguments: Object*): Unit
  def error(msg: String, t: Throwable): Unit
  def isErrorEnabled(marker: Marker): Boolean = factory.levelEnableFor(getName(), "trace")
  def error(marker: Marker, msg: String): Unit
  def error(marker: Marker, format: String, arg: Object): Unit
  def error(marker: Marker, format: String, arg1: Object, arg2: Object): Unit
  def error(marker: Marker, format: String, arguments: Object*): Unit
  def error(marker: Marker, msg: String, t: Throwable): Unit
}

class SimpleLogger(val name: String, override val factory: LoggerFactory) extends Logger {
  def getName(): String = name

  def trace(msg: String): Unit                                = println(msg)
  def trace(format: String, arg: Object): Unit                = println(format.format(arg))
  def trace(format: String, arg1: Object, arg2: Object): Unit = println(format.format(arg1, arg2))
  def trace(format: String, arguments: Object*): Unit         = println(format.format(arguments))
  def trace(msg: String, t: Throwable): Unit                  = println(msg)

  def trace(marker: Marker, msg: String): Unit                                = println(msg)
  def trace(marker: Marker, format: String, arg: Object): Unit                = println(format.format(arg))
  def trace(marker: Marker, format: String, arg1: Object, arg2: Object): Unit = println(format.format(arg1, arg2))
  def trace(marker: Marker, format: String, argArray: Object*): Unit          = println(format.format(argArray))
  def trace(marker: Marker, msg: String, t: Throwable): Unit                  = println(msg)

  def debug(msg: String): Unit                                = println(msg)
  def debug(format: String, arg: Object): Unit                = println(format.format(arg))
  def debug(format: String, arg1: Object, arg2: Object): Unit = println(format.format(arg1, arg2))
  def debug(format: String, arguments: Object*): Unit         = println(format.format(arguments))
  def debug(msg: String, t: Throwable): Unit                  = println(msg)

  def debug(marker: Marker, msg: String): Unit                                = println(msg)
  def debug(marker: Marker, format: String, arg: Object): Unit                = println(format.format(arg))
  def debug(marker: Marker, format: String, arg1: Object, arg2: Object): Unit = println(format.format(arg1, arg2))
  def debug(marker: Marker, format: String, arguments: Object*): Unit         = println(format.format(arguments))
  def debug(marker: Marker, msg: String, t: Throwable): Unit                  = println(msg)

  def info(msg: String): Unit                                = println(msg)
  def info(format: String, arg: Object): Unit                = println(format.format(arg))
  def info(format: String, arg1: Object, arg2: Object): Unit = println(format.format(arg1, arg2))
  def info(format: String, arguments: Object*): Unit         = println(format.format(arguments))
  def info(msg: String, t: Throwable): Unit                  = println(msg)

  def info(marker: Marker, msg: String): Unit                                = println(msg)
  def info(marker: Marker, format: String, arg: Object): Unit                = println(format.format(arg))
  def info(marker: Marker, format: String, arg1: Object, arg2: Object): Unit = println(format.format(arg1, arg2))
  def info(marker: Marker, format: String, arguments: Object*): Unit         = println(format.format(arguments))
  def info(marker: Marker, msg: String, t: Throwable): Unit                  = println(msg)

  def warn(msg: String): Unit                                = println(msg)
  def warn(format: String, arg: Object): Unit                = println(format.format(arg))
  def warn(format: String, arguments: Object*): Unit         = println(format.format(arguments))
  def warn(format: String, arg1: Object, arg2: Object): Unit = println(format.format(arg1, arg2))
  def warn(msg: String, t: Throwable): Unit                  = println(msg)

  def warn(marker: Marker, msg: String): Unit                                = println(msg)
  def warn(marker: Marker, format: String, arg: Object): Unit                = println(format.format(arg))
  def warn(marker: Marker, format: String, arg1: Object, arg2: Object): Unit = println(format.format(arg1, arg2))
  def warn(marker: Marker, format: String, arguments: Object*): Unit         = println(format.format(arguments))
  def warn(marker: Marker, msg: String, t: Throwable): Unit                  = println(msg)

  def error(msg: String): Unit                                = println(msg)
  def error(format: String, arg: Object): Unit                = println(format.format(arg))
  def error(format: String, arg1: Object, arg2: Object): Unit = println(format.format(arg1, arg2))
  def error(format: String, arguments: Object*): Unit         = println(format.format(arguments))
  def error(msg: String, t: Throwable): Unit                  = println(msg)

  def error(marker: Marker, msg: String): Unit                                = println(msg)
  def error(marker: Marker, format: String, arg: Object): Unit                = println(format.format(arg))
  def error(marker: Marker, format: String, arg1: Object, arg2: Object): Unit = println(format.format(arg1, arg2))
  def error(marker: Marker, format: String, arguments: Object*): Unit         = println(format.format(arguments))
  def error(marker: Marker, msg: String, t: Throwable): Unit                  = println(msg)
}
