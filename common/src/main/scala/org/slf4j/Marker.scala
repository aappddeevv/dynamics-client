// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package org.slf4j

trait Marker {
  def getName(): String
  def add(reference: Marker): Unit       = ()
  def remove(reference: Marker): Boolean = false
  def hasReferences(): Boolean           = false
  def iterator(): Iterator[Marker]       = null
  def contains(other: Marker): Boolean   = false
  def contains(name: String): Boolean    = false
}

class SimpleMarker(val name: String) extends Marker {
  def getName(): String = name
}
