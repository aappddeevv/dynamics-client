// Copyright (c) 2017 aappddeevv@gmail.com
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package cli

import scala.scalajs._
import js._
import annotation._
import io.scalajs.nodejs
import io.scalajs.nodejs._
import fs._
import scala.concurrent._
import duration._
import js.DynamicImplicits
import js.Dynamic.{literal => lit}
import scala.util.{Try, Success, Failure}
import fs2._
import fs2.util._
import scala.concurrent.duration._
import io.scalajs.npm.winston
import io.scalajs.npm.winston._
import io.scalajs.npm.winston.transports._
import cats.implicits._

import dynamics.common._
import dynamics.client._
import dynamics.http._
import Status._

import CommandLine._
import Utils._

object MainHelpers extends LazyLogger {

  /** Run the program with a way to provide some more options. */
  //def run[T <: AppConfig](zero: T, options: Seq[CommandLine.OptionProvider[T]] = Nil): Unit = {
  def run(zero: AppConfig,
          moreOpts: Option[scopt.OptionParser[AppConfig] => Unit] = None,
          moreCommands: Option[ActionSelector] = None): Unit = {
    // remove nodejs bin and nodejs script name, make it look java like
    val args = process.argv.drop(2)

    val parser = mkParser()
    addAllOptions(parser)
    moreOpts.foreach(_(parser))

    val config: AppConfig = parser
      .parse(args, zero)
      .fold {
        process.exit(1)
        zero
      } { c =>
        c.copy(common = c.common.copy(connectInfo = readConnectionInfoOrExit(c.common.crmConfigFile)))
      }

    val start = process.hrtime()
    if (config.noisy) {
      val msg = s"dynamicscli\nRun date: " + js.Date()
      println(msg)
      //println(s"${BuildInfo.toString}")
      println(s"node version: ${process.version}")
      println(s"Dynamics user: ${config.common.connectInfo.username.getOrElse("username not provided")}")
      println(
        s"Dynamics endpoint: ${config.common.connectInfo.dataUrl.getOrElse("data endpoint not provided directly")}")
      println()
    }

    if (config.debug) Logger.loglevel = "debug"
    config.loggerLevel.map(lev => org.slf4j.LoggerFactory.loggingLevel = lev.toLowerCase)

    // validate config
    // ...
    if (config.noisy &&
        config.connectInfo.password.map(_.size).getOrElse(0) == 0) {
      logger.warn("Password appears to be empty.")
    }

    val context = DynamicsContext.default(config)(scala.concurrent.ExecutionContext.Implicits.global)

    // io.scalajs.nodejs.process.onUnhandledRejection{(reason: String, p: js.Any) =>
    //   println(s"Unhandled promise error: $reason, $p")
    //   js.undefined
    // }

    // Determine the action.
    val action: Action =
      (moreCommands.flatMap(_(config, context)) orElse
        config.common.actionSelector.flatMap(_(config, context)))
        .getOrElse(NoArgAction(println(s"No actions registered to select from.")))

    // Run the action, then cleanup, then print messages/errors
    action(config).flatMap(_ => context.close()).unsafeRunAsync { attempt =>
      actionPostProcessor[Unit](config.noisy, start)(attempt)
      process.exit(0) // since this is running in the background
    }
  }

  import DynamicsError._

  /** Process an Attempt (Either) from an Action run.
    * @param start Start time information array from `process.hrtime`.
    */
  def actionPostProcessor[A](noisy: Boolean, start: Array[Int]): fs2.util.Attempt[A] => Unit =
    _ match {
      case Right(_) =>
        val delta = processhack.hrtime(start)
        if (noisy) {
          println()
          println("Run time: " + delta(0) + " seconds")
        }
      case Left(t) =>
        t match {
          case x: DynamicsError =>
            println(s"An error occurred communicating with the CRM server.")
            println(x.show)
            x.underlying.foreach { ex =>
              println(s"Underlying stacktrace:")
              ex.printStackTrace()
            }
          case x: js.JavaScriptException =>
            println(s"Internal error during processing: ${x}. Processing stopped.")
            println("Report this as a bug.")
            x.printStackTrace()
          case x: TokenRequestError =>
            println(s"Unable to acquire authentication token. Processing stopped.")
            println(x)
          case x @ UnexpectedStatus(s, reqOpt, respOpt) =>
            println(s"A server call returned an unexpected status and processing stopped: $s.")
            println(s"Request: $reqOpt")
            println(s"Response: $respOpt")
            println("Stack trace:")
            x.printStackTrace()
          case x @ _ =>
            println(s"Processing failed with a program exception ${t}")
            x.printStackTrace()
        }
        val delta = processhack.hrtime(start)
        if (noisy) {
          println()
          println("Run time: " + delta(0) + " seconds")
        }
    }

  /** Provide a default set of actions. If no commands are recognized
    * this runs an action to inform the user to use --help. It always
    * returns a Some.
    */
  val defaultActionSelector: ActionSelector = (config, context) =>
    Some(config.common.command match {
      case "webresources" =>
        val ops = new WebResourcesCommand(context)
        ops.get(config.common.subcommand)

      case "solutions" =>
        val ops = new SolutionActions(context)
        ops.get(config.common.subcommand)

      case "publishers" =>
        val ops = new PublisherActions(context)
        config.common.subcommand match {
          case "list" => ops.list()
        }

      case "asyncoperations" =>
        val ops = new AsyncOperationsCommand(context)
        config.common.subcommand match {
          case "list"                      => ops.list()
          case "deleteCompleted"           => ops.deleteCompleted()
          case "deleteCanceled"            => ops.deleteCanceled()
          case "deleteFailed"              => ops.deleteFailed()
          case "deleteWaiting"             => ops.deleteWaiting()
          case "deleteWaitingForResources" => ops.deleteWaitingForResources()
          case "deleteInProgress"          => ops.deleteInProgress()
          case "cancel"                    => ops.cancel()
        }
      case "workflows" =>
        val ops = new WorkflowActions(context)
        config.common.subcommand match {
          case "list"             => ops.list()
          case "execute"          => ops.executeWorkflow()
          case "changeactivation" => ops.changeActivation()
        }

      case "importmaps" =>
        val ops = new ImportMapActions(context)
        config.common.subcommand match {
          case "list"     => ops.list()
          case "download" => ops.download()
          case "upload"   => ops.upload()
        }

      case "importdata" =>
        val ops = new ImportDataActions(context)
        ops.get(config.common.subcommand)

      case "update" =>
        val ops = new UpdateActions(context)
        config.common.subcommand match {
          case "data" => ops.update
          case "test" => ops.test
        }
      case "whoami" =>
        val ops = new WhoAmIActions(context)
        ops.whoami()
      case "metadata" =>
        val ops = new MetadataActions(context)
        config.common.subcommand match {
          case "listentities" => ops.listEntities()
          case "downloadcsdl" => ops.downloadCSDL()
          case "test"         => ops.test()
        }
      case "sdkmessageprocessingsteps" =>
        val ops = new SDKMessageProcessingStepsActions(context)
        config.common.subcommand match {
          case "list"       => ops.list()
          case "activate"   => ops.activate()
          case "deactivate" => ops.deactivate()
        }
      case "token" =>
        val ops = new TokenActions(context)
        config.common.subcommand match {
          case "getOne"  => ops.getOne();
          case "getMany" => ops.getMany();
        }
      case "entity" =>
        val ops = new EntityActions(context)
        config.common.subcommand match {
          case "export"          => ops.export()
          case "count"           => ops.count()
          case "exportFromQuery" => ops.exportFromQuery()
          case "deleteByQuery"   => ops.deleteByQuery()
        }
      case "optionsets" =>
        val ops = new OptionSetsActions(context)
        config.common.subcommand match {
          case "list" => ops.list()
        }
      case "plugins" =>
        val ops = new PluginActions(context)
        ops.get(config.common.subcommand)
      case "__test__" =>
        val ops = new TestCommand(context)
        ops.runTest()
      case "" =>
        NoArgAction { println(s"No command provided. Print help using --help.") }
      case _ =>
        NoArgAction { println(s"Unrecognized command. This may be a bug. Please report it.") }
    })
}
