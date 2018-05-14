// Copyright (c) 2017 The Trapelo Group LLC
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
import scala.concurrent.duration._
import io.scalajs.npm.winston
import io.scalajs.npm.winston._
import io.scalajs.npm.winston.transports._
import cats.implicits._
import cats.MonadError
import cats.effect._
import monocle.macros.syntax.lens._

import dynamics.common._
import dynamics.client._
import dynamics.http._
import Status._
import dynamics.client.implicits._

import CommandLine._
import Utils._

object MainHelpers extends LazyLogger {

  /**
    * Run the program with a way to provide some more options if desired. All standard options
    * are added by default.
    */
  //def run[T <: AppConfig](zero: T, options: Seq[CommandLine.OptionProvider[T]] = Nil): Unit = {
  def run(zero: AppConfig,
          moreOpts: Option[scopt.OptionParser[AppConfig] => Unit] = None,
          moreCommands: Option[ActionSelector] = None)(implicit ec: ExecutionContext,
                                                       F: MonadError[IO, Throwable]): Unit = {
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
        c.lens(_.common.connectInfo).set(readDynamicsConnectionInfoOrExit(c.common.crmConfigFile))
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

    // Add environment variables
    val impersonateOpt = config.common.impersonate orElse nodejs.process.env.get("DYNAMICS_IMPERSONATE")
    val config2 =
      config
        .lens(_.common.impersonate)
        .set(impersonateOpt)

    // io.scalajs.nodejs.process.onUnhandledRejection{(reason: String, p: js.Any) =>
    //   println(s"Unhandled promise error: $reason, $p")
    //   js.undefined
    // }

    // todo: use IO.bracket with context, wrap in an outer monad

    val context = DynamicsContext.default(config2)

    // Determine the action.
    val action: Action =
      (moreCommands.flatMap(_(config2, context)) orElse
        config2.common.actionSelector.flatMap(_(config2, context)))
        .getOrElse(NoArgAction(println(s"No actions registered to select from.")))

    // Run the action, print messages/errors, cleanup.
    action(config2)
      .flatMap(_ => context.close())
      .attempt
      .flatMap(actionResultProcessor[Unit](config2.noisy, start))
      .unsafeRunAsync{ _ =>
        process.exit(0) // since this is running in the background, exit explicitly
      }
  }

  /**
   * Process an `Attempt[A] = Either[Throwable, A]` from an Action run. Left
   * exceptions are matched and printed out otherwise the program result is
   * printed.
   *
   * @param noisy Whether to print the runtime out.
   * @param start Start time information array from `process.hrtime`.
   */
  def actionResultProcessor[A](noisy: Boolean, start: Array[Int]):
      Either[Throwable, A] => IO[Unit] =
    _ match {
      case Right(_) =>
        IO {
          val delta = processhack.hrtime(start)
          if (noisy) { println("\nRun time: " + delta(0) + " seconds.") }
        }
      case Left(t) =>
        def runtime() = {
          val delta = processhack.hrtime(start)
          if (noisy) println("\nRun time: " + delta(0) + " seconds.")
        }
        t match {
          case x: DynamicsError =>
            IO {
              println(s"An error occurred communicating with the CRM server.")
              println(x.show)
              x.underlying.foreach { ex =>
                println(s"Underlying stacktrace:")
                ex.printStackTrace()
              }
              runtime()
            }
          case x: js.JavaScriptException =>
            IO {
              println(s"Internal error during processing: ${x}. Processing stopped.")
              println("Report this as a bug.")
              x.printStackTrace()
              runtime()
            }
          case x: TokenRequestError =>
            IO {
              println(s"Unable to acquire authentication token. Processing stopped.")
              println(x)
              runtime()              
            }
          case x @ UnexpectedStatus(s, reqOpt, respOpt) =>
            val reqb = reqOpt.fold(IO(println(s"Body: NA")))(_.body.map(b => println(s"Body:\n$b")))
            val respb = respOpt.fold(IO(println(s"Body: NA")))(_.body.map(b => println(s"Body:\n$b")))
            List(
              IO {
                println(s"A server call returned an unexpected status and processing stopped: $s.")
                println(reqOpt.map(r => s"Request: $r").getOrElse("Request: NA"))
              },
              reqb,
              IO { println(respOpt.map(r => s"Response: $r").getOrElse("Response: NA")) },
              respb,
              IO {
                println("Stack trace:")
                x.printStackTrace()
                runtime()
              })
              .sequence
              .void
          case x @ _ =>
            IO {
              println(s"Processing failed with a program exception ${t}")
              x.printStackTrace()
              runtime()              
            }
        }
    }

  /**
    * Provide a default set of actions. If no commands are recognized
    * this runs an action to inform the user to use --help. It always
    * returns a Some.
    */
  val defaultActionSelector: ActionSelector = (config, context) =>
    Some(config.common.command match {
      case "webresources" =>
        val ops = new WebResourcesCommand(context)
        ops.get(config.common.subcommand)

      case "settings" =>
        val ops = new SettingsActions(context)
        ops.get(config.common.subcommand)

      case "solutions" =>
        val ops = new SolutionActions(context)
        ops.get(config.common.subcommand)

      case "publishers" =>
        val ops = new PublisherActions(context)
        config.common.subcommand match {
          case "list" => ops.list()
        }

      case "systemjobs" =>
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
        ops.get(config.common.subcommand)

      case "applications" =>
        val ops = new ApplicationActions(context)
        ops.get(config.common.subcommand)

      case "themes" =>
        val ops = new ThemeActions(context)
        ops.get(config.common.subcommand)

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
        ops.get(config.common.subcommand)
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
      case "users" =>
        val ops = new UsersActions(context)
        ops.get(config.common.subcommand)

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
