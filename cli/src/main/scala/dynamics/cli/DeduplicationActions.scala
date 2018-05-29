// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package cli

import scala.util.control.NonFatal
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

class DeduplicationActions(val context: DynamicsContext) {

  import context._
  import dynamics.client.common.{Id => ID}

  val list = Action { config =>
    dynclient
      .getList[DuplicateRuleJS]("/duplicaterules?$orderby=name")
      .flatMap { items =>
        val eitems = items.map(remapODataFields(_))
        Listings.mkList(config.common, items, Seq("duplicateruleid", "name", "description", "statuscode"),
          Some(jsobj("3" -> jsobj(width = 40)))) { item =>
          Seq(item.duplicateruleid.get, item.name.get, item.description.orEmpty, item.statuscode_fv.get)
        }
      }
      .map(println)
      .void
  }

  // val copy = Action { config =>
  //   val name  = config.themes.source.get
  //   val theme = getByName(name).map(_.toValidNel[String]("Theme named $name not found."))
  //   val io = for {
  //     t <- theme
  //   } yield {
  //     t.map { t =>
  //       println(s"Original:\n${PrettyJson.render(t)}")
  //       val cl      = clone(t, config.themes.target)
  //       val merges  = config.themes.mergeFile.map(f => IOUtils.slurpAsJson[js.Object](f)).getOrElse[js.Object](null)
  //       val payload = Utils.merge[js.Object](cl, merges)
  //       println(s"Clone:\n${PrettyJson.render(payload)}")
  //       dynclient
  //         .createReturnId[js.Object]("themes", payload)
  //         .map { id =>
  //           println(s"Created new theme '$name': /themes($id)")
  //         }
  //     }
  //   }
  //   io.flatMap{
  //     case Validated.Invalid(msglist) =>  IO(msglist.toList.foreach(println))
  //     case Validated.Valid(x) => x
  //   }
  // }

  def getByName(name: String) = {
    val q = QuerySpec(filter = Some(s"name eq '${name}'"))
    dynclient.getOne[Option[DuplicateRuleJS]](q.url("duplicaterules"))(ExpectOnlyOneToOption)
  }

  def getById(id: ID) = {
    dynclient.getOneWithKey[Option[DuplicateRuleJS]]("duplicaterules", id.asString)(ExpectOnlyOneToOption)
  }

  def getByNameOrId(identifier: String) = {
    getByName(identifier)
      .flatMap {
        case Some(x) => IO.pure(Some(x))
        case _ => getById(ID(identifier))
      }
  }

  // val setLogo = Action { config =>
  //   val name  = config.themes.source.get
  //   val wname = config.themes.webresourceName.get
  //   val theme = IO.shift *> getByName(name).map(_.toValidNel[String](s"Theme $name not found."))
  //   val wr    = IO.shift *> getWebResource(wname).map(_.toValidNel[String](s"Web resource $wname not found."))
  //   val io = (theme, wr).parMapN { (tv, wv) =>
  //     (tv, wv).mapN { (t, w) =>
  //       dynclient
  //         .associate("themes", t.themeid, "logoimage", "webresourceset", w.webresourceid, true)
  //         .map(_ => s"Logo updated to $wname on theme $name")
  //         .recover {
  //           case NonFatal(e) => s"Unable to set logo for theme $name to $wname."
  //         }
  //     }
  //   }
  //   io.flatMap{
  //     case Validated.Invalid(msglist) => IO(msglist.toList.foreach(println))
  //     case Validated.Valid(msgio) => msgio.map(println)
  //   }
  // }


  def publishAction(name: String, actionName: String) = {
    val rule = getByNameOrId(name).map(_.toValidNel[String](s"Duplicate rule entity $name not found."))
    val io = for {
      r <- rule
    } yield {
      r.map { r =>
        actionName match {
          case DeduplicationActions.Publish =>
            dynclient.executeAction[Unit](actionName,
              Entity.empty,
              Some(("duplicaterules", r.duplicateruleid.get)))
          case DeduplicationActions.Unpublish =>
            val body = jsobj("DuplicateRuleId" -> r.duplicateruleid.get)
            dynclient.executeAction[Unit](actionName, Entity.fromDynamic(body))
        }
      }
    }
    io.flatMap {
      case Validated.Valid(outputf) => outputf.map(_ => println(s"Successful action on rule $name"))
      case Validated.Invalid(msglist) => IO(msglist.toList.foreach(println))
    }    
  }

  val publish = Action { config =>
    Stream.emits(config.deduplication.identifiers)
      .map(publishAction(_, DeduplicationActions.Publish))
      .map(Stream.eval)
      .join(config.common.concurrency)
      .compile
      .drain
      .void
  }

  val unpublish = Action { config =>
    Stream.emits(config.deduplication.identifiers)
      .map(publishAction(_, DeduplicationActions.Unpublish))
      .map(Stream.eval)
      .join(config.common.concurrency)
      .compile
      .drain
      .void
  }

  val unpublishAll = Action { config =>
    val qs = QuerySpec(select = Seq("duplicateruleid", "name"))
    dynclient.getListStream[DuplicateRuleJS](qs.url("duplicaterules"))
      .map(r => publishAction(r.duplicateruleid.get, DeduplicationActions.Unpublish))
      .map(Stream.eval(_))
      .join(config.common.concurrency)
      .compile
      .drain
      .void
  }

  def get(command: String): Action =
    command match {
      case "publish" => publish
      case "unpublish" => unpublish
      case "unpublishAll" => unpublishAll
      case "list"    => list
      case _ =>
        Action { _ =>
          IO(println(s"deduplication command '${command}' not recognized"))
        }
    }
}

object DeduplicationActions {
  val Publish = "Microsoft.Dynamics.CRM.PublishDuplicateRule"
  //val Unpublish = "Microsoft.Dynamics.CRM.UnpublishDuplicateRule"
  val Unpublish = "UnpublishDuplicateRule"
}
