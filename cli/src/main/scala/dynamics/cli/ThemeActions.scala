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
import client.common._
import dynamics.common.jsdatahelpers._

class ThemeActions(val context: DynamicsContext) {

  import context._

  val keepers = Seq(
    "defaultentitycolor",
    "defaultcustomentitycolor",
    "logotooltip",
    "controlborder",
    "controlshade",
    "selectedlinkeffect",
    "globallinkcolor",
    "processcontrolcolor",
    "headercolor",
    "panelheaderbackgroundcolor",
    "hoverlinkeffect",
    "navbarshelfcolor",
    "backgroundcolor",
    "accentcolor",
    "pageheaderbackgroundcolor",
    "maincolor",
  )

  /** Dumb copy routine to create a payload. */
  def clone(t: Theme, newname: Option[String] = None): js.Object = {
    val logoimage: js.Dynamic =
      t._logoimage_value
        .map(v => jsobj("logoimage@odata.bind" -> s"/webresources($v)"))
        .getOrElse(jsobj())

    Utils.merge[js.Object](
      keepOnly(t.asDict[js.Any], keepers: _*).asJsObj,
      jsobj(
        "name"       -> js.defined(newname.getOrElse(t.name + " copy")),
        "statecode"  -> 0,
        "statuscode" -> 1,
        "type"       -> true,
      ),
      logoimage
    )
  }

  val list = Action { config =>
    dynclient
      .getList[Theme]("/themes?$orderby=name")
      .flatMap { items =>
        Listings.mkList(config.common, items, Seq("themeid", "name", "default", "custom")) { theme =>
          Seq(theme.themeid, theme.name, theme.isdefaulttheme.toString, theme.`type`.toString)
        }
      }
      .map(println)
      .void
  }

  /** Read file, convert to baes64. */
  def base64FromFile(path: String): IO[String] =
    IO {
      val content = Fs.readFileSync(path)
      new Buffer(content).toString("base64")
    }

  val copy = Action { config =>
    val name  = config.themes.source.get
    val theme = getByName(name).map(_.toValidNel[String]("Theme named $name not found."))
    val io = for {
      t <- theme
    } yield {
      t.map { t =>
        println(s"Original:\n${PrettyJson.render(t)}")
        val cl      = clone(t, config.themes.target)
        val merges  = config.themes.mergeFile.map(f => IOUtils.slurpAsJson[js.Object](f)).getOrElse[js.Object](null)
        val payload = Utils.merge[js.Object](cl, merges)
        println(s"Clone:\n${PrettyJson.render(payload)}")
        dynclient
          .createReturnId[js.Object]("themes", payload)
          .map { id =>
            println(s"Created new theme '$name': /themes($id)")
          }
      }
    }
    io.flatMap{
      case Validated.Invalid(msglist) =>  IO(msglist.toList.foreach(println))
      case Validated.Valid(x) => x
    }
  }

  def getByName(name: String) = {
    val q = QuerySpec(filter = Some(s"name eq '${name}'"))
    dynclient.getOne[Option[Theme]](q.url("themes"))(ExpectOnlyOneToOption)
  }

  def getWebResource(name: String) = {
    val q = QuerySpec(filter = Some(s"name eq '${name}'"), select = Seq("name", "webresourceid"))
    dynclient.getOne[Option[WebResourceOData]](q.url("webresourceset"))(ExpectOnlyOneToOption)
  }

  val setLogo = Action { config =>
    val name  = config.themes.source.get
    val wname = config.themes.webresourceName.get
    val theme = IO.shift *> getByName(name).map(_.toValidNel[String](s"Theme $name not found."))
    val wr    = IO.shift *> getWebResource(wname).map(_.toValidNel[String](s"Web resource $wname not found."))
    val io = (theme, wr).parMapN { (tv, wv) =>
      (tv, wv).mapN { (t, w) =>
        dynclient
          .associate("themes", t.themeid, "logoimage", "webresourceset", w.webresourceid, true)
          .map(_ => s"Logo updated to $wname on theme $name")
          .recover {
            case scala.util.control.NonFatal(e) => s"Unable to set logo for theme $name to $wname."
          }
      }
    }
    io.flatMap{
      case Validated.Invalid(msglist) => IO(msglist.toList.foreach(println))
      case Validated.Valid(msg) => msg.map(println)
    }
  }

  val publish = Action { config =>
    val name  = config.themes.source.get
    val theme = getByName(name).map(_.toValidNel[String](s"Theme named $name not found."))
    val io = for {
      t <- theme
    } yield {
      t.map { t =>
        dynclient.executeAction[Unit]("Microsoft.Dynamics.CRM.PublishTheme",
          /*Entity.fromString("""{"blah": 10}""")*/
          Entity.empty,
          Some(("themes", t.themeid)))
      }
    }
    io.flatMap {
      case Validated.Valid(outputf) => outputf.map(_ => println(s"Published theme $name"))
      case Validated.Invalid(msglist) => IO(msglist.toList.foreach(println))
    }
  }

  def get(command: String): Action =
    command match {
      case "publish" => publish
      case "list"    => list
      case "copy"    => copy
      case "setlogo" => setLogo
      case _ =>
        Action { _ =>
          IO(println(s"themes command '${command}' not recognized"))
        }
    }
}

object ThemeActions {}
