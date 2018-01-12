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
import cats.effect._
import scala.util.matching.Regex

import dynamics.common._
import MonadlessIO._
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
  def withData: Kleisli[IO, AppConfig, (AppConfig, Seq[WebResourceOData])] =
    Kleisli { config =>
      getList()
        .map { wr =>
          filter(wr, config.common.filter)
        }
        .map((config, _))
    }

  type WRKleisli = Kleisli[IO, (AppConfig, Seq[WebResourceOData]), Unit]

  /**
    *  List Web Resources but apply a filter.
    */
  def list() = Action { config =>
    val topts = new TableOptions(border = Table.getBorderCharacters(config.common.tableFormat))
    getList().map(filter(_, config.common.filter)).map { wr =>
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
        IO(println(s"[${name}] Deleted."))
    }

    lift {
      // Get web resource list -- all of them
      val wrs = unlift(getList())

      // Filter locally using the delete regexs
      val filtered =
        Utils.filterForMatches(wrs.map(wr => (wr, Seq(wr.name))), config.webResource.webResourceDeleteNameRegex)
      if (filtered.length == 0)
        println(s"No Web Resources matched the regex arguments.")

      val bunchOfDeletes: List[IO[Unit]] = filtered.map(wr => deleteone(wr.name, wr.webresourceid)).toList

      // Maybe publish everything after processing bunchOfDeletes
      unlift(
        bunchOfDeletes.sequence.flatMap(
          _ =>
            if (config.webResource.webResourceDeletePublish)
              publishXml(filtered.map(_.webresourceid)).map(_ => println("Published changes."))
            else IO.pure(())))
    }
  }

  /** Dumps the raw json response for the selected Web Resources. */
  def _dumpRaw(): WRKleisli =
    Kleisli {
      case (config, wr) => {
        println(s"Dumping raw json responses to ${config.webResource.webResourceDumpRawOutputFile}")
        val content = wr.toList map { item =>
          lift {
            val json = unlift(dynclient.getOneWithKey[WebResourceOData]("webresourceset", item.webresourceid))
            //unlift(Task.delay(FS.appendFileSync(
            //  config.webResourceDumpRawOutputFile, query + "\n" + Utils.pprint(json) + "\n")))
            println(s"NOTE: Not writing raw web resourcet file ${item.name}. Uncomment the line of code above.")
            ()
          }
        }
        content.sequence.flatMap(_ => IO.pure(()))
      }
    }

  def dumpRaw() = withData andThen _dumpRaw

  def _download(): WRKleisli =
    Kleisli {
      case (config, wr) => {
        // download and save to the files sytem looking out for path separators
        println(s"Downloading ${wr.size} Web Resources.")
        println(s"Output directory: ${config.common.outputDir}")
        val noclobber = config.common.noclobber
        val downloads = wr.toSeq map { item =>
          lift {
            val wr = unlift(dynclient.getOneWithKey[WebResourceOData]("webresourceset", item.webresourceid))
            //println(s"Processing web resource json: ${Utils.pprint(wr)}")
            val origFilename = wr.name
            val filename     = Utils.pathjoin(config.common.outputDir, origFilename)
            val exists       = Utils.fexists(filename)
            val downloadOrNot: IO[Unit] =
              if (exists && noclobber)
                IO { println("Web Resource $origFilename already downloaded and noclobber is set.") } else
                writeToFile(filename, wr.content).flatMap(_ =>
                  IO(println(s"Saving Web Resource: $origFilename -> $filename")))
            unlift(downloadOrNot)
          }
        }
        downloads.toList.sequence.flatMap(_ => IO.pure(()))
      }
    }

  def download() = withData andThen _download

  /** Write content to file creating paths if path
    * contains a path.
    * @param path Path name, both path and file.
    * @param base64Content Base64 encoded string.
    * @return Successful future if file written, otherwise a Future failure.
    */
  def writeToFile(path: String, base64Content: String): IO[Unit] = {
    val binary = Buffer.from(base64Content, "base64")
    IO.fromFuture(IO(Fse.outputFile(path, binary)))
  }

  /** Read file, convert to baes64. */
  def base64FromFile(path: String): IO[String] =
    IO {
      var content = Fs.readFileSync(path)
      new Buffer(content).toString("base64")
    }

  def upload() = Action { config =>
    val f: String => IO[Seq[WebResourceOData]] = getWRByName(_)
    val sources = determineCreateOrUpdateActions(config.webResource.webResourceUploadSource flatMap interpretGlob,
                                                 config.webResource.webResourceUploadPrefix,
                                                 f,
                                                 config.webResource.webResourceUploadType)
    _process(config, sources)
  }

  protected def log[A](prefix: String): Pipe[IO, A, A] = _.evalMap { a =>
    IO { println(s"$prefix> $a"); a }
  }

  //def watchAndUpload(config: AppConfig): (Stream[Task, Unit], Task[Unit])  = {
  def watchAndUpload() = Action { config =>
    import dynamics.common.FSWatcher.{add, unlink, change, error}

    val f: String => IO[Seq[WebResourceOData]] = getWRByName(_)

    // create a stream of events that closes the chokidar watcher when completed
    val str2 = Stream.bracket(
      IO(chokidar.watch(config.webResource.webResourceUploadSource.toJSArray,
                        new ChokidarOptions(ignoreInitial = true, awaitWriteFinish = true))))(
      cwatcher => FSWatcherOps.toStream[IO](cwatcher, Seq(add, unlink, change, error)),
      cwatcher => IO(cwatcher.close()))

    // array of regexs
    val skips = config.webResource.webResourceUploadWatchIgnore.map(new Regex(_))

    def identifyFileAction(p: (String, String)): Traversable[IO[FileAction]] = {
      val event = p._1
      val path  = p._2

      if (skips.map(_.findFirstIn(path).isDefined).filter(identity).length > 0) {
        Seq(IO((NoAction(s"Path $path was identified to be skipped."), WebResourceFile(path, path))))
      } else
        event match {
          case "add" | "change" =>
            determineCreateOrUpdateActions(Seq(path),
                                           config.webResource.webResourceUploadPrefix,
                                           f,
                                           config.webResource.webResourceUploadType)
          case "unlink" =>
            val resourceName = Utils.stripUpTo(path, config.webResource.webResourceUploadPrefix.getOrElse(""))
            val fileActionF = f(resourceName) map { arr =>
              if (arr.length == 1) (Delete(arr(0).webresourceid), WebResourceFile(path, path))
              else if (arr.length > 1)
                (NoAction("Multiple resources with name $resourceName were found."), WebResourceFile(path, path))
              else (NoAction(s"No existing Web Resource with name $resourceName found."), WebResourceFile(path, path))
            }
            Seq(fileActionF)
          case "error" => Seq(IO((NoAction(path), WebResourceFile(path, path))))
          case _ =>
            Seq(
              IO((NoAction(s"Event $event occurred but no action identified to take for $path."),
                  WebResourceFile(path, path))))
        }
    }

    val estr = str2
      .through(fs2helpers.log[(String, String)] { p =>
        println(format(p._2, s"Event: ${p._1} detected. Identifying action to take..."))
      })
      .map(identifyFileAction)
      .map(_process(config, _))
      .flatMap(Stream.eval(_))

    IO(println("Watching for changes in web resources..."))
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
                                     f: String => IO[Seq[WebResourceOData]],
                                     defaultExt: Option[String] = None,
                                     isIllegal: String => Boolean = isIllegalName,
                                     skip: String => Boolean = _ => false): Traversable[IO[FileAction]] = {

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
        IO.pure((NoAction(s"Resource is to be skipped."), wrf))
      } else if (badName) {
        IO.pure((NoAction(s"Resource name is not valid. Does it have spaces, dashes or underscores in it?"), wrf))
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
  def _process(config: AppConfig, sources: Traversable[IO[FileAction]]): IO[Unit] = {
    //val publish = (id: String) => publishXml(dynclient, Seq(id))
    val create          = (data: String) => dynclient.createReturnId("webresourceset", data)
    val allowedToCreate = config.webResource.webResourceUploadRegister
    val shouldPublish   = config.webResource.webResourceUploadPublish
    val canAddToSoln    = config.webResource.webResourceUploadSolution != ""

    def maybeAddToSoln(id: String, mkMsg: String => String = identity): IO[Unit] =
      if (canAddToSoln)
        addToSolution(id, config.webResource.webResourceUploadSolution)
          .map(_ => ())
          .map(_ => println(mkMsg(s"Added to solution: ${config.webResource.webResourceUploadSolution}.")))
      else IO.pure(println(mkMsg(s"Added to solution: Default.")))

    val factions: Seq[IO[Option[String]]] = sources.toList map {
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
            val unknownType: IO[Option[String]] =
              IO(
                println(
                  format(item.fspath,
                         s"Unknown resource type " +
                           s"""[${item.ext.getOrElse("<no file extension>")}]""")))
                .flatMap(_ => IO.pure(None))

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
            IO(println(format(item.fspath, s"$msg"))).map(_ => None)

          case _ =>
            IO {
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
    factions.toList.sequence.attempt
      .flatMap {
        case Right(idOptList) => IO.pure(idOptList)
        case Left(t: DynamicsError) =>
          println(s"Error trying to take action on webresources")
          println(t.show)
          IO.pure(Seq())
      }
      .map { _.collect { case Some(id) => id } }
      . // strip None's from list
      flatMap { ids =>
      if (shouldPublish && ids.size > 0)
        publishXml(ids).map(_ => println(format("*", "Published changes.")))
      else IO(println(s"No publishing occurred."))
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
    dynclient.executeAction("PublishXml", Entity.fromString(body), None)(void)
  }

  /** Not sure there is a way to unpublish a resource via API. */
  def unpublish(id: String) = IO.unit

  /** Add a WR to a Solution. */
  def addToSolution(id: String, soln: String, componentType: Int = 61) = {
    val args = new AddSolutionComponentArgs(ComponentId = id, ComponentType = componentType, SolutionUniqueName = soln)
    dynclient.executeAction[AddSolutionComponentResponse]("AddSolutionComponent", args.toEntity._1)
  }

  val selectUpload = Action { config =>
    if (config.webResource.webResourceUploadWatch) {
      watchAndUpload()(config)
    } else upload()(config)
  }

  /**
    * Delete all source maps (.js.map) web resources given a solution unique name.
    * Only this extension is allowed in order to avoid accidental deletes.
    */
  val deleteSourceMaps = Action { config =>
    // get solution id from name
    // get all web resources in the solution via the solution components
    // note its possible during dev that webresource.solutionid is *not* set correctly
    // delete those that match the name

    val sname = config.webResource.webResourceUploadSolution
    val qsoln = QuerySpec(filter = Some(s"uniquename eq '$sname'"))
    dynclient
      .getList[SolutionOData](qsoln.url("solutions"))
      .flatMap { solns =>
        if (solns.length != 1) IO(println(s"Unique solution name $sname was not found."))
        else {
          val q = QuerySpec(filter = Some(s"componenttype eq 61 and _solutionid_value eq ${solns(0).solutionid}"))
          dynclient
            .getListStream[SolutionComponentOData](q.url("solutioncomponents"))
            .map { sc =>
              Stream.eval(dynclient.getOneWithKey[WebResourceOData]("webresourceset", sc.objectid))
            }
            .join(config.common.concurrency)
            .filter(wr => wr.name.endsWith(".js.map"))
            .evalMap { wr =>
              val deleteit = dynclient.delete("webresourceset", wr.webresourceid).map(_ => s"Deleted ${wr.name}.")
              wr.componentstate match {
                case 0     => unpublish(wr.webresourceid).flatMap(_ => deleteit)
                case 1     => deleteit
                case 2 | 3 => IO(s"Web resource ${wr.name} is already in a deleted state. No action taken.")
                case _     => IO(s"Web resource ${wr.name}. Unknown component state ${wr.componentstate}")
              }
            }
            .to(Sink.lines(System.out))
            .run
        }
      }
      .flatMap(_ => IO.unit)
  }

  def get(command: String): Action = {
    command match {
      case "list"             => list()
      case "dumpraw"          => dumpRaw()
      case "download"         => download()
      case "upload"           => selectUpload
      case "delete"           => delete()
      case "deleteSourceMaps" => deleteSourceMaps
      case _ =>
        Action { _ =>
          IO(println(s"webresource command '${command}' not recognized."))
        }
    }
  }

}

object WebResource {

  /** web resource component type. should get from metadata, not hardcode */
  val WebResourceComponentType = IO(61)
}
