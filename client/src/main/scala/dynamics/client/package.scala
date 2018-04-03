// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics

package object client {
  /** Options with as much as possible being returned. */
  val DefaultDynamicsOptions = DynamicsOptions()

  /** Options with as little as possble being returned. */
  val QuietDynamicsOptions = DynamicsOptions(prefers=client.common.headers.QuietPreferOptions)

  /** Boolean header value to suppress duplicate detection. */
  val SuppressDuplicateDetection = "MSCRM.SuppressDuplicateDetection"

  /** Header for impersonating. Value sould be the systemuserid. */
  val Impersonate = "MSCRMCallerID"

}
