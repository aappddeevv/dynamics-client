// Copyright (c) 2017 aappddeevv@gmail.com
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package client

import scala.scalajs.js
import js._
import JSConverters._
import io.scalajs.nodejs._
import scala.concurrent._
import io.scalajs.util.PromiseHelper.Implicits._
import fs2._
import cats._
import cats.data._
import cats.implicits._
import fs2.interop.cats._
import js.Dynamic.{literal => jsobj}

import dynamics.common.implicits._

sealed trait OrderByDir
case object Desc extends OrderByDir
case object Asc  extends OrderByDir

case class OrderBy(name: String, dir: OrderByDir)

object OrderBy {
  def render(by: OrderBy): String = {
    by.dir match {
      case Desc => s"${by.name} desc"
      case Asc  => s"${by.name} asc"
    }
  }
}

trait QueryParts {
  def select: Seq[String]
  def filter: Option[String]
  def orderBy: Seq[String]
  def top: Option[Int]
}

object QueryParts {

  /** Convert QueryParts into a URL fragment. */
  def render(spec: QueryParts): Seq[Option[String]] =
    Seq(
      Option(spec.select.mkString(",")).filterNot(_.isEmpty).map("$select=" + _),
      Option(spec.orderBy.mkString(",")).filterNot(_.isEmpty).map("$orderby=" + _),
      spec.filter.filterNot(_.isEmpty).map("$filter=" + _),
      spec.top.map(i => (i > 0, i)).filter(_._1).map("$top=" + _._2)
    )
}

/** To address a property. */
case class NavProperty(name: String, id: Option[String] = None, cast: Option[String] = None)

object NavProperty {

  /** Includes a leading '/' */
  def render(p: NavProperty): String = {
    val id   = p.id.map("(" + _ + ")").getOrElse("")
    val cast = p.cast.map("/" + _).getOrElse("")
    s"/${p.name}${id}${cast}"
  }
}

/** Very crude query spec. This still requires knowledge of
  * the REST URL format. Can expand multiple so Option[Expand]
  * should be Seq[Expand]. QuerySpec contains everything
  * you need for generating a URL except! the first
  * navigation property. "cast" applies to that first
  * navigation property when you render it.
  */
case class QuerySpec(select: Seq[String] = Nil,
                     filter: Option[String] = None,
                     orderBy: Seq[String] = Nil, // attribute desc/asc
                     includeCount: Boolean = false,
                     expand: Seq[Expand] = Nil,
                     top: Option[Int] = None,
                     skip: Option[Int] = None,
                     fetchXml: Option[String] = None,
                     savedQuery: Option[String] = None,
                     userQuery: Option[String] = None,
                     cast: Option[String] = None,
                     properties: Seq[NavProperty] = Nil)
    extends QueryParts {

  /** Add an Expand. */
  def withExpand(e: Expand)   = this.copy(expand = this.expand ++ Seq(e))
  def withOrderBy(by: String) = this.copy(orderBy = Seq(by))
  def withNav(p: NavProperty) = this.copy(properties = this.properties ++ Seq(p))
}

object QuerySpec {
  def render(spec: QuerySpec) = {
    val parts = QueryParts.render(spec) ++
      Seq(Expand.render(spec.expand)) ++
      Seq(
        if (spec.includeCount) Option("$count=true") else None,
        spec.skip.map(i => (i > 0, i)).filter(_._1).map("$skip=" + _._2),
        spec.fetchXml.filterNot(_.isEmpty).map("fetchXml=" + _),
        spec.savedQuery.filterNot(_.isEmpty).map("savedQuery=" + _),
        spec.userQuery.filterNot(_.isEmpty).map("userQuery=" + _)
      )
    val cast         = spec.cast.map("/" + _).getOrElse("")
    val properties   = spec.properties.map(NavProperty.render(_)).mkString("")
    val definedparts = parts.collect { case Some(x) => x }
    val sep          = if (definedparts.size > 0) "?" else ""
    s"""/${properties}${cast}${sep}${definedparts.mkString("&")}"""
  }
}

/** Another crude spec for $expand. */
case class Expand(
    property: String,
    select: Seq[String] = Nil,
    filter: Option[String] = None,
    orderBy: Seq[String] = Nil,
    top: Option[Int] = None
) extends QueryParts

object Expand {
  def render(e: Seq[Expand]): Option[String] = {
    val parts: Seq[String] = e
      .map { expandme =>
        val oneexp = QueryParts.render(expandme).collect { case Some(x) => x }
        if (oneexp.size > 0)
          Option(s"""${expandme.property}(${oneexp.mkString(";")})""")
        else
          Option(expandme.property)
      }
      .collect { case Some(x) => x }
    if (parts.size > 0)
      Some(s"""$$expand=${parts.mkString(",")}""")
    else
      None
  }
}

final class QuerySpecOps(val spec: QuerySpec) extends AnyVal {

  /** Convert Query spec to a URL. You must specify the
    * entity and key that it applies to as the first navigation property.
    */
  def url(ename: String, keyInfo: Option[String] = None) = {
    val rest       = QuerySpec.render(spec)
    val keyInfoStr = keyInfo.map(ki => s"($ki)").getOrElse("")
    s"""/${ename}${keyInfoStr}${rest}"""
  }

  /** Render QuerySpec. Throws an exception of there are no navigation properties. */
  def url = {
    if (spec.properties.size == 0)
      throw new IllegalArgumentException(s"QuerySpec has no navigation properties: $spec")
    QuerySpec.render(spec)
  }

}

trait QuerySpecSyntax {
  implicit def dynamicsSyntaxQuerySpec(spec: QuerySpec) = new QuerySpecOps(spec)
}
