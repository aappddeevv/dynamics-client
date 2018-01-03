// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

import scala.scalajs.js
import js.annotation._
import fs2._

import dynamics.common._
import dynamics.client._
import dynamics.http._

/**
  * Header associated with an ETL payload. Contains information
  * that can help formulate a request.
  */
trait Header extends js.Object {

  /** Action to take: UPDATE, DELETE, INSERT. */
  val action: String

  /** Version tag for optimistic concurrency. */
  val version: js.UndefOr[String]

  /** GUID of user to impersonate. */
  val user: js.UndefOr[String]

  /** When used with an HTTP client. */
  val httpHeaders: js.Dictionary[String]
}

/**
  * A unit consists of a header and the data playload.
  */
trait ProcessingUnit[T <: js.Any] extends js.Object {
  val header: Header
  val payload: js.Array[T]
}

trait BatchOptions {

  /** If true, use batch request model. */
  def batch: Boolean

  /** Batch size if batch is true. */
  def batchSize: Int
}

object Processors {

  /** A list of (HttpRequest, optional correlation id) tuples. */
  type RequestSet = Vector[(HttpRequest, Option[String])]

  /**
    * Create a batch request from a (use changset, RequestSet) tuple.
    * TODO: Incorporate correlation ids into a structure that
    * can pull out the responses properly.
    */
  val batch: (Boolean, RequestSet) => HttpRequest =
    (useChangeSet, rset) => {
      val reqs  = rset.map(ro => SinglePart(ro._1))
      val label = "batch_" + java.util.UUID.randomUUID.toString()
      val m =
        if (useChangeSet)
          Multipart(ChangeSet(reqs, Boundary.mkBoundary("changeset_")) :: Nil, Boundary(label))
        else
          Multipart(reqs, Boundary(label))
      DynamicsClientRequests.mkBatchRequest(HttpHeaders.empty, m)
    }
}
