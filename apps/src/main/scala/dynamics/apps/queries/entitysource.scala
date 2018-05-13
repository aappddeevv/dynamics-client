// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package apps
package queries

import scala.scalajs.js
import js.|
import fs2._
import cats.effect._

import dynamics.client._
import dynamics.http._
import dynamics.client.common.QuerySpec
import dynamics.client.syntax.queryspec._
import dynamics.common.implicits._
import apps.facades.handlebars._

/**
  * Queries to obtain streams of source entity data. Each time a method is called
  * a template is compiled, so don't use this on a per item processing basis.
  */
class DynamicsEntitySource(val dynclient: DynamicsClient, verbosity: Int = 0) {

  def fetchItemsFromODataQuery[A <: js.Object](qry: ODataQuery, context: js.Object): Either[String, Stream[IO, A]] = {
    val url = compileAndApplyTemplate(qry.odata.get, context)
    if (verbosity > 1) println(s"Query url: [$url]")
    Right(dynclient.getListStream[A](url, dynamics.client.NoisyDynamicsOptions))
  }

  def fetchItemsFromFetchXMLQuery[A <: js.Object](qry: FetchXMLQuery,
                                                  context: js.Object): Either[String, Stream[IO, A]] = {
    val tmp: String = compileAndApplyTemplate(qry.fetchXML.get, context)
    if (verbosity > 1) println(s"Query fetch xml: [$tmp]")
    val qs = QuerySpec(fetchXml = Some(tmp))
    Right(dynclient.getListStream[A](qs.url(qry.entitySetName.get)))
  }

  /** Given a query, return the appropriate dynamicsclient stream. */
  def fetchItems[A <: js.Object](query: ODataQuery | FetchXMLQuery,
                                 context: js.Object): Either[String, Stream[IO, A]] = {
    (query.asInstanceOf[js.Any]) match {
      case q
          if query.merge[js.Object].hasOwnProperty("odata") &&
            (q.asDyn.odata != null) =>
        fetchItemsFromODataQuery(q.asInstanceOf[ODataQuery], context)

      case q
          if query.merge[js.Object].hasOwnProperty("fetchXML") &&
            query.merge[js.Object].hasOwnProperty("entity") &&
            (q.asDyn.fetchXML != null) && (q.asDyn.entity != null) =>
        fetchItemsFromFetchXMLQuery(q.asInstanceOf[FetchXMLQuery], context)

      case _ =>
        Left("Query must have odata or fetchXML valid fields.")
    }
  }
}
