// Copyright (c) 2017 aappddeevv@gmail.com
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
import fs2.util._
import js.{Array => arr}
import JSConverters._
import Dynamic.{literal => jsobj}
import cats._
import cats.data._
import cats.implicits._
import io.scalajs.npm.chalk._
import fs2.interop.cats._
import scala.util.matching.Regex

import dynamics.common._
import MonadlessTask._
import dynamics.common.implicits._
import dynamics.http._
import dynamics.http.implicits._
import dynamics.client._
import dynamics.client.implicits._

trait AddSolutionComponentResponse extends js.Object {

  /** id of solution component record */
  val id: js.UndefOr[String] = js.undefined
}

/**
  * See: https://msdn.microsoft.com/en-us/library/mt593057.aspx
  *
  * See: https://msdn.microsoft.com/en-us/library/gg328546.aspx#bkmk_componenttype
  */
class AddSolutionComponentArgs(
    val ComponentId: String,
    val ComponentType: Int = 61, // web resource type:
    val SolutionUniqueName: String,
    val AddRequiredComponents: Boolean = false,
    val IncludedComponentSettingsValues: Null | js.Array[String] = null
) extends js.Object

class WebResourcesCommand(val context: DynamicsContext) {

  import context._

  val filterOn     = Seq("displayname", "name")
  val defaultAttrs = Seq("name", "displayname", "webresourceid", "webresourcetype", "solutionid")

  def getList(attrs: Seq[String] = defaultAttrs) = {
    val query =
      s"""/webresourceset?$$select=${attrs.mkString(",")}"""
    dynclient.getList[WebResourceOData](query)
  }

  protected def filter(wr: Traversable[WebResourceOData], filters: Seq[String]) =
    Utils.filterForMatches(wr.map(a => (a, Seq(a.displayname, a.name, a.webresourceid))), filters)

  /** Combinator to obtain web resources automatically */
  def withData: Kleisli[Task, AppConfig, (AppConfig, Seq[WebResourceOData])] =
    Kleisli { config =>
      getList()
        .map { wr =>
          filter(wr, config.filter)
        }
        .map((config, _))
    }

  type WRKleisli = Kleisli[Task, (AppConfig, Seq[WebResourceOData]), Unit]

  /**
    *  List Web Resources but apply a filter.
    */
  def list() = Action { config =>
    val topts = new TableOptions(border = Table.getBorderCharacters(config.tableFormat))
    getList().map(filter(_, config.filter)).map { wr =>
      val data =
        Seq(Seq("#", "webresourceid", "displayname", "name", "webresourcetype", "solutionid").map(Chalk.bold(_))) ++
          wr.zipWithIndex.map {
            case (i, idx) =>
              Seq((idx + 1).toString, i.webresourceid, i.displayname, i.name, i.webresourcetype.toString, i.solutionid)
          }
      val out = Table.table(data.map(_.toJSArray).toJSArray, topts)
      println(out)
    }
  }

  def delete() = Action { config =>
    val deleteone = (name: String, id: String) =>
      dynclient.delete("webresourceset", id).flatMap { id =>
        Task.delay(println(s"[${name}] Deleted."))
    }

    lift {
      // Get web resource list -- all of them
      val wrs = unlift(getList())

      // Filter locally using the delete regexs
      val filtered = Utils.filterForMatches(wrs.map(wr => (wr, Seq(wr.name))), config.webResourceDeleteNameRegex)
      if (filtered.length == 0)
        println(s"No Web Resources matched the regex arguments.")

      val bunchOfDeletes: List[Task[Unit]] = filtered.map(wr => deleteone(wr.name, wr.webresourceid)).toList

      // Maybe publish everything after processing bunchOfDeletes
      unlift(
        bunchOfDeletes.sequence.flatMap(
          _ =>
            if (config.webResourceDeletePublish)
              publishXml(filtered.map(_.webresourceid)).map(_ => println("Published changes."))
            else Task.now(())))
    }
  }

  /** Dumps the raw json response for the selected Web Resources. */
  def _dumpRaw(): WRKleisli =
    Kleisli {
      case (config, wr) => {
        println(s"Dumping raw json responses to ${config.webResourceDumpRawOutputFile}")
        val content = wr.toList map { item =>
          lift {
            val json = unlift(dynclient.getOneWithKey[WebResourceOData]("webresourceset", item.webresourceid))
            //unlift(Task.delay(FS.appendFileSync(
            //  config.webResourceDumpRawOutputFile, query + "\n" + Utils.pprint(json) + "\n")))
            println(s"NOTE: Not writing raw web resourcet file ${item.name}. Uncomment the line of code above.")
            ()
          }
        }
        Task.traverse(content)(identity).flatMap(_ => Task.now(()))
      }
    }

  def dumpRaw() = withData andThen _dumpRaw

  def _download(): WRKleisli =
    Kleisli {
      case (config, wr) => {
        // download and save to the files sytem looking out for path separators
        println(s"Downloading ${wr.size} Web Resources.")
        println(s"Output directory: ${config.outputDir}")
        val noclobber = config.noclobber
        val downloads = wr.toSeq map { item =>
          lift {
            val wr = unlift(dynclient.getOneWithKey[WebResourceOData]("webresourceset", item.webresourceid))
            //println(s"Processing web resource json: ${Utils.pprint(wr)}")
            val origFilename = wr.name
            val filename     = Utils.pathjoin(config.outputDir, origFilename)
            val exists       = Utils.fexists(filename)
            val downloadOrNot: Task[Unit] =
              if (exists && noclobber)
                Task.delay { println("Web Resource $origFilename already downloaded and noclobber is set.") } else
                writeToFile(filename, wr.content).flatMap(_ =>
                  Task.delay(println(s"Saving Web Resource: $origFilename -> $filename")))
            unlift(downloadOrNot)
          }
        }
        Task.traverse(downloads)(identity).flatMap(_ => Task.now(()))
      }
    }

  def download() = withData andThen _download

  /** Write content to file creating paths if path
    * contains a path.
    * @param path Path name, both path and file.
    * @param base64Content Base64 encoded string.
    * @return Successful future if file written, otherwise a Future failure.
    */
  def writeToFile(path: String, base64Content: String): Task[Unit] = {
    val binary = Buffer.from(base64Content, "base64")
    Task.fromFuture(Fse.outputFile(path, binary))
  }

  /** Read file, convert to baes64. */
  def base64FromFile(path: String): Task[String] =
    Task.delay {
      var content = Fs.readFileSync(path)
      new Buffer(content).toString("base64")
    }

  def upload() = Action { config =>
    val f: String => Task[Seq[WebResourceOData]] = getWRByName(_)
    val sources = determineCreateOrUpdateActions(config.webResourceUploadSource flatMap interpretGlob,
                                                 config.webResourceUploadPrefix,
                                                 f,
                                                 config.webResourceUploadType)
    _process(config, sources)
  }

  protected def log[A](prefix: String): Pipe[Task, A, A] = _.evalMap { a =>
    Task.delay { println(s"$prefix> $a"); a }
  }

  //def watchAndUpload(config: AppConfig): (Stream[Task, Unit], Task[Unit])  = {
  def watchAndUpload() = Action { config =>
    import Task._
    import dynamics.common.FSWatcher.{add, unlink, change, error}

    val f: String => Task[Seq[WebResourceOData]] = getWRByName(_)

    // create a stream of events that closes the chokidar watcher when completed
    val str2 = Stream.bracket(
      Task.delay(chokidar.watch(config.webResourceUploadSource.toJSArray,
                                new ChokidarOptions(ignoreInitial = true, awaitWriteFinish = true))))(
      cwatcher => FSWatcherOps.toStream(cwatcher, Seq(add, unlink, change, error)),
      cwatcher => Task.delay(cwatcher.close()))

    // array of regexs
    val skips = config.webResourceUploadWatchIgnore.map(new Regex(_))

    def identifyFileAction(p: (String, String)): Traversable[Task[FileAction]] = {
      val event = p._1
      val path  = p._2

      if (skips.map(_.findFirstIn(path).isDefined).filter(identity).length > 0) {
        Seq(Task.now((NoAction(s"Path $path was identified to be skipped."), WebResourceFile(path, path))))
      } else
        event match {
          case "add" | "change" =>
            determineCreateOrUpdateActions(Seq(path), config.webResourceUploadPrefix, f, config.webResourceUploadType)
          case "unlink" =>
            val resourceName = Utils.stripUpTo(path, config.webResourceUploadPrefix.getOrElse(""))
            val fileActionF = f(resourceName) map { arr =>
              if (arr.length == 1) (Delete(arr(0).webresourceid), WebResourceFile(path, path))
              else if (arr.length > 1)
                (NoAction("Multiple resources with name $resourceName were found."), WebResourceFile(path, path))
              else (NoAction(s"No existing Web Resource with name $resourceName found."), WebResourceFile(path, path))
            }
            Seq(fileActionF)
          case "error" => Seq(Task.delay((NoAction(path), WebResourceFile(path, path))))
          case _ =>
            Seq(
              Task.delay((NoAction(s"Event $event occurred but no action identified to take for $path."),
                          WebResourceFile(path, path))))
        }
    }

    val estr = str2
      .through(fs2helpers.log[(String, String)] { p =>
        println(format(p._2, s"Event: ${p._1} detected. Identifying action to take..."))
      })
      .through(pipe.lift(identifyFileAction))
      .through(pipe.lift(_process(config, _)))
      .flatMap(Stream.eval(_))

    Task
      .delay(println("Watching for changes in web resources..."))
      .flatMap(_ => estr.run)
  }

  /**
    * Expand glob to a list of WebResourceFile descriptors.
    * If pathname ends in in slash (/), expand to all the files in all the directories below.
    * If pathname is a directory, expand to all the files in all the directories below.
    */
  def interpretGlob(g: String): Traversable[String] = glob(g, jsobj("nodir" -> true))

  sealed trait UploadAction
  case class Update(id: String)       extends UploadAction
  case object Create                  extends UploadAction
  case class Delete(id: String)       extends UploadAction
  case class NoAction(reason: String) extends UploadAction

  /** Web Resource OS file descriptor.
    * @param fspath The OS filesystem path that was used to create the remaining args.
    * @param resourceName From the publisher prefix segment forward from fspath.
    * @param ext Extension of filename.
    */
  case class WebResourceFile(fspath: String, resourceName: String, ext: Option[String] = None)

  /** UploadAction + WebResourceFile */
  type FileAction = (UploadAction, WebResourceFile)

  val badChars = "!@#$%^&*()[{]}?=+\\|;:,<>'\" -" // removed forward slash /

  val isIllegalName: (String) => Boolean = resourceName =>
    (badChars.map(resourceName.contains(_)).filter(identity).length > 0) && !resourceName.contains("//")

  /**
    * Determine the action to take or each resource.
    * Requires retrieving the web resource from the server to if the resource exists.
    * This method does not issue deletes. It's one way between OS and server.
    * With Seq[] parameter, we may be able to optimize the fetch.
    * @param files List of OS paths.
    * @param prefix Optional prefix. Used to find the resource name in the OS path.
    * @param f Given a resource name, return the Web Resource from the server.
    * @param defaultExt ???
    * @param skips Given a resource name (which could look like path), skip it if skip returns true.
    * @return Traversable of FileAction wrapped in the server check effect.
    *
    * TODO: Optimize checking each resource for its status on the server.
    */
  def determineCreateOrUpdateActions(files: Traversable[String],
                                     prefix: Option[String],
                                     f: String => Task[Seq[WebResourceOData]],
                                     defaultExt: Option[String] = None,
                                     isIllegal: String => Boolean = isIllegalName,
                                     skip: String => Boolean = _ => false): Traversable[Task[FileAction]] = {

    files map { item =>
      val pathobj      = Path.parse(item)
      val resourceName = prefix.map(Utils.stripUpTo(item, _)).getOrElse(item)
      val wrf =
        WebResourceFile(item,
                        resourceName,
                        pathobj.ext.toOption.filterNot(_.length == 0).map(_.substring(1).toLowerCase) orElse defaultExt)

      // zap prefix which may have an underscore then check name for bad chars
      val nameToCheck = resourceName.replaceFirst(prefix.getOrElse(""), "X")
      val badName     = isIllegal(nameToCheck) //badChars.map(nameToCheck.contains(_)).filter(identity).length > 0 && !nameToCheck.contains("//")
      //println(s"bad name check: ${nameToCheck}, $badName")
      val skipit = skip(nameToCheck)

      if (skipit) {
        Task.now((NoAction(s"Resource is to be skipped."), wrf))
      } else if (badName) {
        Task.now((NoAction(s"Resource name is not valid. Does it have spaces, dashes or underscores in it?"), wrf))
      } else {
        f(wrf.resourceName).map { wrs =>
          if (wrs.length == 1) (Update(wrs(0).webresourceid), wrf)
          else if (wrs.length == 0) (Create, wrf)
          else if (wrs.length > 1) (NoAction(s"More than one resource was found with name ${wrf.resourceName}"), wrf)
          else (NoAction(s"Unable to determine action for Web Resource path ${item}"), wrf)
        }
      }
    }
  }

  def format(about: String, msg: String) = s"[${js.Date()}]: $about: $msg"

  /**
    * Process each FileAction and optionally publish them.
    */
  def _process(config: AppConfig, sources: Traversable[Task[FileAction]]): Task[Unit] = {
    //val publish = (id: String) => publishXml(dynclient, Seq(id))
    val create          = (data: String) => dynclient.createReturnId("webresourceset", data)
    val allowedToCreate = config.webResourceUploadRegister
    val shouldPublish   = config.webResourceUploadPublish
    val canAddToSoln    = config.webResourceUploadSolution != ""

    def maybeAddToSoln(id: String, mkMsg: String => String = identity): Task[Unit] =
      if (canAddToSoln)
        addToSolution(id, config.webResourceUploadSolution)
          .map(_ => ())
          .map(_ => println(mkMsg(s"Added to solution: ${config.webResourceUploadSolution}.")))
      else Task.now(println(mkMsg(s"Added to solution: Default.")))

    val factions: Seq[Task[Option[String]]] = sources.toList map {
      _ flatMap { action =>
        //println(s"Processing: $action")
        val item  = action._2
        val rname = item.resourceName
        val rtype = item.ext.flatMap(Utils.inferWebResourceType(_))

        // Generate set of Tasks with the appropriate file action and output message.
        // The output value should be an optional id (string) for potential publishing.
        action._1 match {
          case Create if allowedToCreate =>
            def createit(rtype: Int) = {
              base64FromFile(item.fspath).flatMap { resourceContent =>
                val body = JSON.stringify(new WebResourceUpsertArgs(rname, rname, rtype, content = resourceContent))
                create(body).flatMap { id =>
                  println(format(item.fspath, s"Created Web Resource ${item.resourceName}"))
                  maybeAddToSoln(id, format(item.fspath, _: String)).map { _ =>
                    Option(id)
                  }
                }
              }
            }
            val unknownType: Task[Option[String]] =
              Task
                .delay(
                  println(
                    format(item.fspath,
                           s"Unknown resource type " +
                             s"""[${item.ext.getOrElse("<no file extension>")}]""")))
                .flatMap(_ => Task.now(None))

            rtype.fold(unknownType)(createit(_))

          case Update(id) =>
            def getContentOnlyBody(path: String) =
              base64FromFile(path)
                .map(b64str => JSON.stringify(js.Dictionary("content" -> b64str)))
            // Compose the update effect
            getContentOnlyBody(item.fspath).flatMap { body =>
              uploadWR(id, body).map { _ =>
                println(format(item.fspath, s"Updating $rname"))
                Option(id)
              }
            }
          case Delete(id) =>
            dynclient.delete("webresourceset", id).map { _ =>
              println(format(item.fspath, s"Deleted $rname."))
              Option(id)
            } //:Task[Option[String]]

          case NoAction(msg) =>
            Task.delay(println(format(item.fspath, s"$msg"))).map(_ => None)

          case _ =>
            Task.delay {
              println(
                format(
                  item.fspath,
                  s"Not allowed to create, update or delete. " +
                    s"Check your parameters and the resource at path [${item.fspath}] with inferred Web Resource name [${item.resourceName}]"
                ))
              None
            }
        }
      }
    }

    // Maybe publish all changes after processing all the file actions.
    Task
      .traverse(factions)(identity)
      .attempt
      .flatMap {
        case Right(idOptList) => Task.now(idOptList)
        case Left(t: DynamicsError) =>
          println(s"Error trying to take action on webresources")
          println(t.show)
          Task.now(Seq())
      }
      .map { _.collect { case Some(id) => id } }
      . // strip None's from list
      flatMap { ids =>
      if (shouldPublish && ids.size > 0)
        publishXml(ids).map(_ => println(format("*", "Published changes.")))
      else Task.delay(println(s"No publishing occurred."))
    }
  }

  def getWRByName(name: String) = {
    val query = s"/webresourceset?$$filter=name eq '$name'&$$select=webresourceid"
    dynclient.getList[WebResourceOData](query)
  }

  /** Upload action. Return entity id. */
  def uploadWR(id: String, json: String) =
    dynclient.update("webresourceset", id, json)

  /** Publish a WR. */
  def publishXml(id: Traversable[String]) = {
    val ids   = id.map("<webresource>{" + _ + "}</webresource>").mkString("")
    val xml   = s"<importexportxml><webresources>$ids</webresources></importexportxml>"
    val query = "/PublishXml"
    val body  = JSON.stringify(jsobj("ParameterXml" -> xml))
    dynclient.executeAction("PublishXml", Entity.fromString(body), None)(EntityDecoder.void)
  }

  /** Add a WR to a Solution. */
  def addToSolution(id: String, soln: String) = {
    val args = new AddSolutionComponentArgs(ComponentId = id, ComponentType = 61, SolutionUniqueName = soln)
    dynclient.executeAction[AddSolutionComponentResponse]("AddSolutionComponent", args.toEntity._1)
  }

  val selectUpload = Action { config =>
    if (config.webResourceUploadWatch) {
      watchAndUpload()(config)
    } else upload()(config)
  }

  def get(command: String): Action = {
    command match {
      case "list"     => list()
      case "dumpraw"  => dumpRaw()
      case "download" => download()
      case "upload"   => selectUpload
      case "delete"   => delete()
    }
  }

}
