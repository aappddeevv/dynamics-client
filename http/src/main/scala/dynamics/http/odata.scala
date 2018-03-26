// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package http

import fs2._
import cats._
import cats.effect._
import cats.implicits._

object OData {

  val FormattedValue               = "OData.Community.Display.V1.FormattedValue"
  val AssociatedNavigationProperty = "Microsoft.Dynamics.CRM.associatednavigationproperty"
  val LookupLogicalName            = "Microsoft.Dynamics.CRM.lookuplogicalname"
  val NextLink                     = "@odata.nextLink"

  /** Create an attribute name with modifier. */
  def attr(p: String, mod: String) = p + "@" + mod

  def getBasicHeaders(): HttpHeaders =
    HttpHeaders("OData-Version"    -> "4.0",
                "OData-MaxVersion" -> "4.0",
                "Cache-Control"    -> "no-cache",
                "If-None-Match"    -> "null") ++
      AcceptHeader ++
      ContentTypeJson

  val ContentTypeJson = HttpHeaders("Content-Type" -> "application/json; charset=utf-8")
  val AcceptHeader    = HttpHeaders("Accept"       -> "application/json")

  /** Default includes everything. */
  case class PreferOptions(maxPageSize: Option[Int] = None,
                           includeRepresentation: Option[Boolean] = None,
                           includeFormattedValues: Option[Boolean] = None,
                           includeLookupLogicalNames: Option[Boolean] = None,
                           includeAssociatedNavigationProperties: Option[Boolean] = None)

  /** Quiet prefer options. */
  val QuietPreferOptions = PreferOptions(
    includeRepresentation = Some(false),
    includeFormattedValues = Some(false),
    includeLookupLogicalNames = Some(false),
    includeAssociatedNavigationProperties = Some(false)
  )

  /** Adds everything to be returned. */
  val NoisyPreferOptions = PreferOptions(
    includeRepresentation = Some(true),
    includeFormattedValues = Some(true),
    includeLookupLogicalNames = Some(true),
    includeAssociatedNavigationProperties = Some(true)
  )

  /** Default Prefer options, noisy. */
  val DefaultPreferOptions = NoisyPreferOptions

  /** Return formatted values. */
  val FormattedValues = PreferOptions(includeFormattedValues = Some(true))

  /** Renders value side of Prefer header. */
  def render(popts: PreferOptions): Option[String] = {
    val opts = collection.mutable.ListBuffer[Option[String]]()

    opts += popts.maxPageSize.map(x => s"odata.maxpagesize=$x")
    opts += popts.includeRepresentation.flatMap {
      _ match {
        case true => Option("return=representation")
        case _    => None
      }
    }

    val preferExtra = Seq(
      popts.includeFormattedValues.flatMap(f => if (f) Some(FormattedValue) else None),
      popts.includeLookupLogicalNames.flatMap(f => if (f) Some(LookupLogicalName) else None),
      popts.includeAssociatedNavigationProperties.flatMap(f => if (f) Some(AssociatedNavigationProperty) else None)
    ).collect { case Some(x) => x }

    if (preferExtra.size == 3) opts += Some("odata.include-annotations=\"*\"");
    else if (preferExtra.size != 0) opts += Some("odata.include-annotations=\"" + preferExtra.mkString(",") + "\"")

    val str = opts.collect { case Some(x) => x }.mkString(",")
    if (str == "") None
    else Some(str)
  }
}

/** Boundary marker for batch or changesets. */
final case class Boundary(value: String) extends AnyVal

object Boundary extends RenderConstants {
  private[dynamics] def generate(): String = java.util.UUID.randomUUID.toString

  /** Make a boundary with a random UUID. */
  def mkBoundary(prefix: String = "boundary_"): Boundary = Boundary(prefix + generate())

  def renderBoundary(boundary: Boundary, close: Boolean): String = {
    val sb = new StringBuilder()
    sb.append(CRLF + "--" + boundary.value)
    if (close) sb.append("--")
    sb.append(CRLF)
    sb.toString()
  }
}

/**
  * One part of a multipart request. There are only two subtypes, one for a
  * request directly in the multipart message and the other for a
  * changeset. Deletes, updates and inserts must be in a changeset.
  */
sealed trait Part {
  def xtra: HttpHeaders
}

object Part {

  /** Make changeset from SingleParts. */
  //def mkChangeset(parts: Seq[SinglePart], b: Boundary = Boundary.mkBoundary("changeset_")) = ChangeSet(parts, b)

  /** An empty part. */
  val empty = EmptyPart
}

/** Single request. Either standalone or in a changeset. Content-Type and
  * Content-Transfer-Encoding is added to each part prior to the request being
  * written. Headers in request can be overriden and added to using xtra.
  * @paarm request HttpRequest
  * @param xtra Extra headers after the boundary but not in the actual request.
  */
final case class SinglePart(request: HttpRequest, xtra: HttpHeaders = HttpHeaders.empty) extends Part

/**
  * Changeset "part". Headers are written for Content-Type and
  * Content-Transfer-Encoding at the start of each Part's boundary. These can be
  * overwritten or added to using xtra. A random Content-ID is added if one is
  * not present.
  * @param requests Set of changset requests.
  * @param bounday The changeset boundary.
  * @param xtra Extra headers after the changeset boundary but not in the actual requests.
  */
final case class ChangeSet(parts: Seq[SinglePart],
                           boundary: Boundary = Boundary.mkBoundary("changeset_"),
                           xtra: HttpHeaders = HttpHeaders.empty)
    extends Part

/** Renders to empty. */
private[dynamics] final object EmptyPart extends Part { val xtra = HttpHeaders.empty }

object ChangeSet {

  /** ChangeSet from requests. */
  def fromRequests(requests: Seq[HttpRequest]): ChangeSet = ChangeSet(requests.map(SinglePart(_)))
}

/**
  * Multipart composed of a list of parts: individual requests and changesets.
  * Despite its name, it does not inherit from Part.
  * @param parts Sequence of Parts.
  * @param boundary Batch boundary.
  */
final case class Multipart(parts: Seq[Part], boundary: Boundary = Boundary.mkBoundary())

trait RenderConstants {
  val MediaType = "multipart/mixed"
  val CRLF      = "\r\n"
}

object Multipart extends RenderConstants {
  import Boundary.renderBoundary

  /** Requests are bundled into a changeset. */
  def fromRequests(requests: Seq[HttpRequest]): Multipart = Multipart(Seq(ChangeSet.fromRequests(requests)))

  /**
    * Render a batch boundary for each part, render each part, render the closing batch boundary.
    */
  def render(m: Multipart): IO[String] = {
    val partsAsTasks = m.parts.map { p =>
      renderPart(p).map(rest => renderBoundary(m.boundary, false) + rest)
    }
    partsAsTasks.toList.sequence.map(all => all.mkString("") + renderBoundary(m.boundary, true))
  }

  val StandardPartHeaders = HttpHeaders("Content-Transfer-Encoding" -> "binary", "Content-Type" -> "application/http")

  /** Render a "mini-request". Each batch part must have a Host header or a full URL.
    *
    * TODO: THere may be an extra space between a changeset boundary and the start of
    * a changeset item's boundary header.
    */
  def renderPart(p: Part): IO[String] = {
    p match {
      case SinglePart(req, xtra) =>
        val partHeaders = StandardPartHeaders ++ xtra
        val method      = s"${req.method.name} ${req.path} HTTP/1.1"

        // this seems disruptive, but its malformed if its not right...
        if (!req.headers.contains("Host") && !req.path.startsWith("http"))
          throw new IllegalArgumentException(
            "OData multitpart requires each part request to have a Host header or a full path URL.")

        req.body.map { body =>
          HttpHeaders.render(partHeaders) + CRLF + method + CRLF + HttpHeaders.render(req.headers) + CRLF + body
        }

      case ChangeSet(parts, b, xtra) =>
        val partHeaders = HttpHeaders("Content-Type" -> (Multipart.MediaType + "; boundary=" + b.value)) ++ xtra
        val partsAsTasks = parts.map { p =>
          // add Content-ID if not present in the xtra headers in the part.
          val withContentId =
            HttpHeaders.from("Content-ID" -> p.xtra.get("Content-ID").getOrElse(Seq(Boundary.generate())))
          // Ensure type=entry if not present in the seq attached to content-type header or
          // add the entire content-type header if its missing.
          val modifiedOrOrigCT =
            p.request.headers.get("Content-Type").map(seq => (seq, seq.indexOf("type"))).collect {
              case (seq, idx) if (idx >= 0) => seq
              case (seq, _)                 => seq :+ "type=entry"
            }
          val newCT: HttpHeaders =
            HttpHeaders.from("Content-Type" -> Seq("application/json", "type=entry")) ++
              modifiedOrOrigCT.map(ctvalue => HttpHeaders.from("Content-Type" -> ctvalue)).getOrElse(HttpHeaders.empty)

          val modified =
            p.copy(request = p.request.copy(headers = p.request.headers ++ newCT), xtra = p.xtra ++ withContentId)
          renderPart(modified).map(rest => renderBoundary(b, false) + rest)
        }
        partsAsTasks.toList.sequence
          .map(all => HttpHeaders.render(partHeaders) + CRLF + all.mkString("") + renderBoundary(b, true))
      case EmptyPart =>
        IO.pure("")
    }
  }
}
