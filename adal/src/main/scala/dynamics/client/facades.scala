// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package client

import scala.scalajs.js
import js._
import annotation._
import dynamics.common._

/**
  *  From js ADAL library. For details on using ADAL in general: https://msdn.microsoft.com/en-us/library/gg327838.aspx.
  */
@js.native
@JSImport("adal-node", "AuthenticationContext")
class AuthenticationContext(authority: String, validateAuthority: Boolean = true) extends js.Object {
  def authority: js.Object          = js.native
  def options: js.Object            = js.native
  def options_=(o: js.Object): Unit = js.native
  def cache: js.Object              = js.native
  def acquireTokenWithUsernamePassword(resource: String,
                                       username: String,
                                       password: String,
                                       applicationId: String,
                                       callback: js.Function2[js.Error, // can be null but not undefined
                                                              UndefOr[ErrorResponse | TokenInfo],
                                                              Unit]): Unit = js.native
}
