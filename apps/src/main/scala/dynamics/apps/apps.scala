// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics

import io.estatico.newtype.macros.newtype

package object apps {

  /** Id newtype. */
  @newtype case class Id(asString: String)

  /** email address, called PN in dynamics, newtype. */
  @newtype case class UPN(asString: String)
}
