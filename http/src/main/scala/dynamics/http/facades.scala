// Copyright (c) 2017 aappddeevv@gmail.com
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package http

import scala.scalajs.js
import js.UndefOr
import js.annotation._

/** User provided configuration informmation. */
@js.native
trait ConnectionInfo extends js.Object {
  val tenant: UndefOr[String]               = js.native
  val username: UndefOr[String]             = js.native
  def password: UndefOr[String]             = js.native
  def password_=(v: UndefOr[String]): Unit  = js.native
  val applicationId: UndefOr[String]        = js.native
  val dataUrl: UndefOr[String]              = js.native
  val acquireTokenResource: UndefOr[String] = js.native
  val authorityHostUrl: UndefOr[String]     = js.native
  val renewalFraction: UndefOr[Int]         = js.native
}

/** General response envelope. Use A=_ if you do not
  * care about the values.
  */
@js.native
trait ValueArrayResponse[A] extends js.Object {
  @JSName("@odata.nextLink")
  val nextLink: UndefOr[String]   = js.native
  val value: UndefOr[js.Array[A]] = js.native
}
