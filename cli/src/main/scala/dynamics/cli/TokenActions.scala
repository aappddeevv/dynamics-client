// Copyright (c) 2017 The Trapelo Group LLC
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

  val defaultOutputFile = "token.json"

  def doit(n: Long = 1) = Action { config =>
    val ofile = config.common.outputFile.getOrElse(defaultOutputFile)
    println(s"Token output file: ${ofile}")
    val auth = new AuthManager(config.common.connectInfo)
    val ctx  = auth.getAuthContext();
    val refresh = config.token.refreshIntervalInMinutes
    val str =
      AuthManager.tokenStream(
        auth.getTokenWithRetry(ctx,
          dynamics.http.retry.retryWithPause(5.seconds, 10)))
          //,_ => FiniteDuration(1, TU.MINUTES))
    str
      .take(n)
      .map { ti =>
        println(s"Writing token to file: ${ofile}. ${js.Date()}")
        writeToFileSync(ofile, JSON.stringify(ti))
        1
      }
      .compile.drain
  }

  def getOne()  = doit(1)
  def getMany() = doit(Long.MaxValue)
}
