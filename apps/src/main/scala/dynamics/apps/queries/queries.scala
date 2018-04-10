// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package apps

import scala.scalajs.js
import js.|
import dynamics.common.implicits._

package object queries {

  def mkIdentifierExtractor[A <: js.Object](idName: String, nameName: String,
    altId: String="<no id>", altName: String = "<no name>"): A => FriendlyIdentifier =
    obj => {
      val d = obj.asDict[js.Any]
      FriendlyIdentifier(
        d.get(idName).map(_.toString).getOrElse(altId),
        d.get(nameName).map(_.toString).getOrElse(altName)
      )
    }

  /** Break out the | to either. */
  implicit class EitherQuery(query: ODataQuery|FetchXMLQuery) {
    def toEither(): Either[FetchXMLQuery, ODataQuery] = {
      query match {
        case q if query.merge[js.Object].hasOwnProperty("fetchXML") => Left(q.asInstanceOf[FetchXMLQuery])
        case q if query.merge[js.Object].hasOwnProperty("odata") => Right(q.asInstanceOf[ODataQuery])
      }}}
}


