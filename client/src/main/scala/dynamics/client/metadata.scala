// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

/** Metadata client based on DynamicsClient. */
package dynamics
package client

import fs2._
import cats.effect._

case class OptionPair(Label: String, Value: Int)

/**
  * Metadata.
  */
case class Metadata(client: DynamicsClient) {

  /** Fetch option set from server. */
  def getOptionSet(entityName: String, attribute: String): IO[Seq[OptionPair]] =
    IO.pure(Seq())

}
