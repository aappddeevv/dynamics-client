// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package cli

import scala.scalajs.js
import js._
import js.annotation._
import io.scalajs.nodejs._
import fs._
import buffer._
import path._
import scala.concurrent._
import duration._
import scala.util.{Try, Success, Failure}
import io.scalajs.util.PromiseHelper.Implicits._
import fs2._
import fs2.async
import js.{Array => arr}
import JSConverters._
import Dynamic.{literal => jsobj}
import cats._
import cats.data._
import cats.implicits._
import io.scalajs.npm.chalk._
import scala.util.matching.Regex
import cats.effect._

import dynamics.common._
import MonadlessIO._
import dynamics.common.implicits._
import dynamics.http._
import dynamics.http.implicits._
import dynamics.client._
import dynamics.client.implicits._

/*
@js.native
trait AddSolutionComponentResponse extends js.Object {

  /** id of solution component record */
  val id: js.UndefOr[String] = js.native
}

class AddSolutionComponentArgs(
    val ComponentId: String,
    val ComponentType: Int = 61, // web resource type:
    val SolutionUniqueName: String,
    val AddRequiredComponents: Boolean = false,
    val IncludedComponentSettingsValues: Null | js.Array[String] = null
) extends js.Object
 */

class PluginActions(val context: DynamicsContext) {

  import context._

  /** Read file, convert to baes64. */
  def base64FromFile(path: String): IO[String] =
    IO {
      val content = Fs.readFileSync(path)
      new Buffer(content).toString("base64")
    }

  def update(id: String, name: String, dllContent: String) = {
    val payload = new PluginAssembly {
      override val content = dllContent
      // shell out to run sn on linux or windows, but for now, this works
      // since its already registered.
      override val publickeytoken = null
    }
    dynclient
      .update("pluginassemblies", id, JSON.stringify(payload))
      .map(_ => true)
  }

  def updateFromFile(id: String, path: String, name: String) = {
    lift {
      val dll = unlift(base64FromFile(path))
      unlift(update(id, name, dll))
    }
  }

  def getByName(name: String): IO[Either[String, PluginAssembly]] = {
    val q = QuerySpec(filter = Some(s"name eq '${name}'"))
    lift {
      val records = unlift(dynclient.getList[PluginAssembly](q.url("pluginassemblies")))
      if (records.length > 1) Left(s"More than one plugin assembly found with name ${name}")
      else if (records.length == 1) Right(records(0))
      else Left(s"No plugin assemblies with name ${name} were found")
    }
  }

  def runOnce(src: Option[String]) = {
    val source = src.getOrElse("")
    val pname  = Utils.namepart(source).getOrElse(source)
    lift {
      println(s"Update assembly ${pname} content from contents of ${source}.")
      unlift(getByName(pname)) match {
        case Left(msg) =>
          println(s"Error: Plugin names must be unique and already exist: ${msg}")
        case Right(assembly) =>
          val dll = unlift(base64FromFile(source))
          if (assembly.content.map(_ == dll).getOrElse(false))
            println("Content is the same. No update performed.")
          else {
            val r = update(assembly.pluginassemblyid.getOrElse(""), assembly.name.getOrElse(""), dll)
              .map(_ => println("Update completed successfully."))
            unlift(r)
          }
      }
    }
  }
  def format(about: String, msg: String) = s"[${js.Date()}]: $about: $msg"

  def watch(config: AppConfig) = {
    import dynamics.common.FSWatcher.{add, unlink, change, error}
    val source = config.plugin.source.getOrElse("")
    // chokidar requires a .close() call
    val str2 =
      Stream.bracket(IO(chokidar.watch(source, new ChokidarOptions(ignoreInitial = true, awaitWriteFinish = true))))(
        cwatcher => FSWatcherOps.toStream[IO](cwatcher, Seq(add, change, unlink)),
        cwatcher => IO(cwatcher.close()))

    val runme = str2
      .through(fs2helpers.log[(String, String)] { p =>
        println(format("Event detected", s"${p._1}: ${p._2}"))
      })
      .map { p =>
        val event = p._1
        val path  = p._2
        event match {
          case "add" | "change" => runOnce(Option(path))
          case "unlink"         => IO(println(s"File removed: $source"))
          case _                => IO(println(s"Untracked event on file $source. No action taken."))
        }
      }
      .flatMap(Stream.eval(_))

    IO(println(s"Watching for changes at: ${source}"))
      .flatMap(_ => runme.run)
  }

  val upload = Action { config =>
    if (config.plugin.watch) watch(config)
    else runOnce(config.plugin.source)
  }

  def get(command: String): Action =
    command match {
      case "upload" => upload
      case _ =>
        Action { _ =>
          IO(println(s"plugins command '${command}' not recognized"))
        }
    }
}

object PluginActions {}
