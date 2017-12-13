// Copyright (c) 2017 aappddeevv@gmail.com
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package cli

import dynamics.common._

import scala.scalajs.js
import scala.scalajs.js._
import fs2._

import Utils._
import dynamics.client.implicits._

import retry._
import scala.concurrent.duration._
import java.util.concurrent.{TimeUnit => TU}

import dynamics.client._

class TokenActions(context: DynamicsContext) extends LazyLogger {

  import context._
  import dynclient._

  def doit(n: Long = 1) = Action { config =>
    println(s"Token output file: ${config.common.outputFile}")
    val auth = new AuthManager(config.common.connectInfo)
    val ctx  = auth.getAuthContext();
    val str =
      AuthManager.tokenStream(auth.getTokenWithRetry(ctx, Pause(3, 2.seconds)), _ => FiniteDuration(55, TU.MINUTES))
    str
      .take(n)
      .map { ti =>
        println(s"Writing token to file: ${config.common.outputFile}. ${js.Date()}")
        config.common.outputFile.foreach(f => writeToFile(f, JSON.stringify(ti)))
        1
      }
      .run
  }

  def getOne()  = doit(1)
  def getMany() = doit(Long.MaxValue)
}
