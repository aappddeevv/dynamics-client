// Copyright (c) 2017 aappddeevv@gmail.com
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package client

final case class DynamicsIdOps(val i: DynamicsId) extends AnyVal {}

final case class DynamicsIdStringOps(val s: String) extends AnyVal {
  def id: DynamicsId = Id(s)
}

trait DynamicsIdSyntax {
  implicit def dynamicsSyntaxDynamicsId(id: DynamicsId) = new DynamicsIdOps(id)
  implicit def dynamicsIdStringOps(s: String)           = new DynamicsIdStringOps(s)
}

trait DynamicsIdImplicits {
  implicit def idToString(id: DynamicsId): String = id.render()
  implicit def stringToId(s: String): DynamicsId  = Id(s)
}

// Add each individual syntax trait to this
trait AllSyntax extends QuerySpecSyntax with DynamicsIdSyntax

// Add each individal syntax trait to this
object syntax {
  object all        extends AllSyntax
  object queryspec  extends QuerySpecSyntax
  object dynamicsid extends DynamicsIdSyntax
}

//trait AllImplicits extends DynamicsIdImplicits with MiscImplicits
trait AllImplicits extends DynamicsIdImplicits
object implicits   extends AllImplicits with AllSyntax
