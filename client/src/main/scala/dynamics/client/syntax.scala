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

trait ClientShows {
  implicit val innerErrorShow: Show[InnerError] = Show { e =>
    s"${e.message} (${e.etype})\n${e.stacktrace}"
  }

  implicit val dynamicsServerErrorShow: Show[DynamicsServerError] = Show { e =>
    s"${e.message} (code=${e.code})\n" +
      s"Inner Error: " + e.innererror.map(_.show).getOrElse("<not present>")
  }

  implicit val showDynamicsError: Show[DynamicsError] = Show { e =>
    s"Dynamics error: ${e.getMessage}\n" +
      s"Status code: ${e.status.show}\n" +
      s"Dynamics server error: " + e.cause.map(_.show).getOrElse("<dynamics server error not provided>") + "\n" +
      s"Underlying error: " + e.underlying.map(_.toString).getOrElse("<underlying error not provided>") + "\n"
  }
}

trait AllSyntax extends client.common.QuerySpecSyntax with DynamicsIdSyntax

object syntax {
  object all        extends AllSyntax
  object queryspec  extends client.common.QuerySpecSyntax
  object dynamicsid extends DynamicsIdSyntax
}

trait AllInstances extends ClientShows

object instances {
  object all    extends AllInstances
  object client extends ClientShows
}

object implicits extends AllSyntax with AllInstances
