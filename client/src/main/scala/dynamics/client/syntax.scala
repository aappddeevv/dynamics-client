// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package client

import cats._
import cats.data._
import cats.syntax.show._

final case class DynamicsIdOps(val id: DynamicsId) extends AnyVal {
  def idToString: String = id.render()
}

final case class DynamicsIdStringOps(val s: String) extends AnyVal {
  def id: DynamicsId = Id(s)
}

trait DynamicsIdSyntax {
  implicit def dynamicsSyntaxDynamicsId(id: DynamicsId) = new DynamicsIdOps(id)
  implicit def dynamicsIdStringOps(s: String)           = new DynamicsIdStringOps(s)

  // alright, this is evil, but convenient...
  implicit def stringToId(v: String): DynamicsId = v.id
}

trait ClientShowInstances {
  implicit def innerErrorShow: Show[InnerError] = Show.show{ err =>
    s"""InnerError
       |InnerError.etype = ${err.etype}
       |InnerError.message = ${err.message}
       |InnerError.stacktrace = ${err.stacktrace}
       |""".stripMargin
  }

  implicit def dynamicsServerErrorShow: Show[DynamicsServerError] = Show.show{ err =>
    s"""DynamicsServerError
       |DynamicsServerError.code = ${err.code}
       |DynamicsServerError.message = ${err.message}
       |DynamicsServerError.innererror = ${err.innererror.map(_.show).getOrElse("N/A")}
       |""".stripMargin
  }

  implicit val showDynamicsError: Show[DynamicsError] = Show.show { e =>
    s"""Dynamics error = ${e.getMessage}
        |DynamicsError.Status code = ${e.status.show}
        |DynamicsError.Server error = ${e.cause.map(_.show).getOrElse("N/A")}
        |DynamicsError.Underlying error = ${e.underlying.map(_.toString).getOrElse("N/A")}
        |""".stripMargin
  }
}

trait AllSyntax extends client.common.QuerySpecSyntax with DynamicsIdSyntax

object syntax {
  object all        extends AllSyntax
  object queryspec  extends client.common.QuerySpecSyntax
  object dynamicsid extends DynamicsIdSyntax
}

trait AllInstances extends ClientShowInstances

object instances {
  object all    extends AllInstances
  object client extends ClientShowInstances
}

object implicits extends AllSyntax with AllInstances
