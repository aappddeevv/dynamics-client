// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package client
package common

/**
 * OData control parameters such as OData "prefer" headers.
 */
trait ClientOptions {
  def prefers: headers.PreferOptions
  def version: Option[String] = None
  def user: Option[String] = None
  def applyOptimisticConcurrency: Option[Boolean] = None
}
