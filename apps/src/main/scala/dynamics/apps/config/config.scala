// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package apps
package config

import scala.scalajs.js
import js.|
import cats.effect._
import concurrent.ExecutionContext

import dynamics.client._
import dynamics.http._
import dynamics.client.common.QuerySpec
import dynamics.client.syntax.queryspec._
import dynamics.common.Utils
import dynamics.common.implicits._
import apps.facades.handlebars._

/**
 * Access configuration information for "apps" from the dynamics server or a
 * local JSON resource.
 */
class Config(val dynclient: DynamicsClient, verbosity: Int = 0)
  (implicit ec: ExecutionContext) {

  /** Read config files, merged, right takes precedence. */
  def getLocalConfiguration[C <: js.Object](files: Seq[String]): IO[Either[String, C]] = {
    val fcontent =
      files
        .map(f => (Utils.fexists(f), f))
        .collect{ case (exists, f) if exists => f}
        .map(Utils.slurpAsJson[js.Object](_))
    if(fcontent.length == 0) IO.pure(Left(s"Configuration not found."))
    IO.pure(Right(fcontent.fold(new js.Object())((a,b) => a.combine(b)).asInstanceOf[C]))
  }

  /** Slurps a single local JSON file. */
  def getLocalConfig[A <: js.Object](name: String): IO[A] =
    IO.pure(Utils.slurpAsJson[A](name))

  /**
   * Get config "string" from a dynamics server making some simple assumptions
   * about how the "config" records are setup e.g. the entity to access, the
   * name of the entity (versus the id) and the attribute with the config data
   * in it. If the name is not unique None is returned. Config "string" can be
   * anything, including a "template" concept. You will typically want to
   * convert this to JSON object so do `getRemoteConfiguration(...).map(str =>
   * JSON.parse(str).asInstanceOf[MyConfig])` or just write your own function.
   */
  def getRemoteConfiguration(name: String, entitySetName: String,
    nameAttribute: String, contentAttribute: String): IO[Option[String]] = {
    val q = QuerySpec(
      select = Seq(nameAttribute),
      filter = Some(s"""$nameAttribute eq '$name'""")
    )
    dynclient.getList[ValueArrayResponse[js.Object]](q.url(entitySetName))
      .map{ seq =>
        if(seq.length == 1) seq(0).asDict[String].get(contentAttribute)
        else Option.empty
      }
  }
}
