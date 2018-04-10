// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package client

/**
 * Use with QuerySpec.filter e.g. `filter=Some(Between("age", 1, 10))`. Values are always single quoted.
*
* @see https://docs.microsoft.com/en-us/dynamics365/customer-engagement/web-api/equalbusinessid?view=dynamics-ce-odata-9
    *
* @todo: Allow query parameters to be returned as a separate part to reduce URL size.
 */
object crmqueryfunctions {

  val prefix = "Microsoft.Dynamics.CRM"

  private def squote(v: Traversable[String]): Traversable[String] = v.map("'" + _ + "'")

  /** Range check, string/numbers should be converted to strings. */
  def Between(pname: String, min: String, max: String): String =
    s"""$prefix.Between(PropertyName='$pname', PropertyValues=["'$min'", "'$max'"])"""

  def Contains(pname: String, value: String): String =
    s"""$prefix.Contains(PropertyName='$pname',PropertyValue='$value'"""

  def ContainsValues(pname: String, values: Traversable[String]): String = 
    s"""$prefix.ContainsValues(PropertyName='$pname',PropertyValues=[${squote(values).mkString(",")}])"""

  /** Returns: datetime'2010-07-15' or datetime'2010-07-15T16:19:54Z' depending on input. */
  def datetime(v: String): String = s"""datetime'$v'"""

  def DoesNotContainValues(pname: String, values: Traversable[String]): String =
    s"""$prefix.DoesNoteContain(PropertyName='$pname',PropertyValues=[${squote(values).mkString(",")}])"""

  def EqualUserId(pname: String): String =
    s"""$prefix.EqualUserId(PropertyName='$pname')"""

  def EqualUserTeams(pname: String): String =
    s"""$prefix.EqualUserTeam(PropertyName='$pname')"""

  /** Single quote if your values are strings. */
  def In(pname: String, values: Traversable[String]): String =
    s"""$prefix.In(PropertyName='$pname',PropertyValues=[${squote(values).mkString(",")}])"""

  /** Range check, strings/numbers should be converted to strings. Single quote if your values are  strings. */
  def NotBetween(pname: String, min: String, max: String): String =
    s"""$prefix.NotBetween(PropertyName='$pname', PropertyValues=["$min", "$max"])"""

  /** Value should be a datetime string. */
  def OnOrAfter(pname: String, value: String): String =
    s"""$prefix.OnOrAfter(PropertyName='$pname',PropertyValue='$value')"""

  /** Value should be a datetime string. */
  def OnOrBefore(pname: String, value: String): String =
    s"""$prefix.OnOrBefore(PropertyName='$pname',PropertyValue='$value')"""

  def Today(pname: String): String =
    s"""$prefix.Today(PropertyName='$pname')"""

  def Tomorrow(pname: String): String =
    s"""$prefix.Tomorrow(PropertyName='$pname')"""

  def Yesterday(pname: String): String =
    s"""$prefix.Yesterday(PropertyName='$pname')"""

}
