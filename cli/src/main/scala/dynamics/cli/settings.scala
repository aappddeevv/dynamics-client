// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package cli

import scala.scalajs.js
import js._
import js.annotation._
import io.scalajs.nodejs._
import fs2._
import js.JSConverters._
import cats._
import cats.data._
import cats.implicits._
import js.Dynamic.{literal => jsobj}
import cats.effect._

import common._
import MonadlessIO._
import common.Utils._
import common.implicits._
import dynamics.http._
import dynamics.http.implicits._
import client._
import client.implicits._
import client.common._

@js.native
trait OrganizationJS extends js.Object {
  var organizationid: String = js.native
  var name: String           = js.native
}

/**
  * Perform a post on an undocumented API to set an org's settings.  You will
  * need to prepare a config file in the specific format to post as the body.
  *
  */
class SettingsActions(context: DynamicsContext) extends LazyLogger {

  import SettingsActions._
  import context._

  //val SEARCH_POSTFIX = "/AppWebServices/SystemCustomization.asmx"
  val defaultSettingsFile = "org-settings.json"

  def post() = Action { config =>
    val settingsFileOpt = config.settings.settingsFile orElse Some(defaultSettingsFile)
    settingsFileOpt match {
      case Some(settingsFile) if (IOUtils.fexists(settingsFile)) =>
        val body = JSON.parse(IOUtils.slurp(settingsFile))
        val nameOpt =
          if (js.DynamicImplicits.truthValue(body.name)) {
            // remove it from the json
            js.special.delete(body, "name")
            Option(body.name.asString)
          } else if (config.settings.name.isDefined) config.settings.name
          else None
        nameOpt.fold(IO(println("No name found from command line or settings file.")))(name => {
          val qopts = QuerySpec(select = Seq("name", "organizationid"))
          dynclient
            .getList[OrganizationJS](qopts.url(SettingsActions.entitySet))
            .flatMap(_.headOption match {
              case Some(org) =>
                dynclient
                  .update(SettingsActions.entitySet, org.organizationid, JSON.stringify(body), true)
                  .map(_ => println("Settings updated."))
              case _ =>
                IO(println(s"Organization name ${name} not found."))
            })
        })
      case _ => IO(println("No settings file specified and the default ${defaultSettingsFile} does not exist."))
    }
  }

  /**
    * Modify entities for categorized search. There is a form endpoint for this
    * but then you need a crmwrpctoken which we cannot get externally. Publish to
    * the old org web services endpoint.
    *
    * @see https://bettercrm.blog/2017/08/02/list-of-undocumented-sdk-messages.
    * @see https://stackoverflow.com/questions/11655943/bad-request-415
    */
  def search() = Action { config =>
    // (context.appConfig.common.connectInfo.acquireTokenResource.toOption,
    //   config.settings.entityList) match {
    //   case (_, Nil) =>
    //     IO(println("A complete (not incremental) list of entities for categorized search must be provided."))
    //   case (Some(url), entityList) =>
    //     val body = s"""<?xml version="1.0" encoding="utf-8" ?><soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soap:Body><SaveEntityGroupConfiguration xmlns="http://schemas.microsoft.com/crm/2009/WebServices"><entityList>${entityList.mkString(",")}</entityList></SaveEntityGroupConfiguration></soap:Body></soap:Envelope>"""
    //     val headers = HttpHeaders(
    //       "SoapAction" -> "http://schemas.microsoft.com/crm/2009/WebServices/SaveEntityGroupConfiguration",
    //         //"http://schemas.microsoft.com/crm/2011/WebServices/SaveEntityGroupConfiguration",
    //       "Accept" -> "*/*",
    //       "Content-Type" -> "text/xml; charset=UTF-8")
    //     val finalUrl = url + "/    XRMServices/2011/Organization.svc"
    //     val request = HttpRequest(Method.POST, finalUrl, headers, Entity.fromString(body))
    //     dynclient.http.fetch(request) {
    //       case Status.Successful(r) =>
    //         if(config.debug) println(s"search post response $r")
    //         IO.pure("Categorized search entity list applied.")
    //       case bad =>
    //         if(config.debug) println(s"settings categorized search response $bad")
    //         bad.body.map(body => s"Error setting categorized search entities.\nStatus: ${bad.status.code}\nResponse Body: ${body}")
    //     }
    //       . map(println)
    //   case (_, _) => IO(println("acquireTokenResource must be provided in the connection info."))
    // }
    IO(println("NOT IMPLEMENTED"))
  }

  def list() = Action { config =>
    val qopts = QuerySpec()
    dynclient
      .getList[OrganizationJS](qopts.url("organizations"))
      .map { list =>
        list.foreach(org => println(IOUtils.render(org)))
      }
  }

  def get(command: String): Action = {
    command match {
      case "post"              => post()
      case "categorizedsearch" => search()
      case "list"              => list()
      case _ =>
        Action { _ =>
          IO(println(s"settings command '${command}' not recognized."))
        }
    }
  }
}

object SettingsActions {
  val entitySet = "organizations"
}
