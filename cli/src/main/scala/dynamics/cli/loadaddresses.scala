// Copyright (c) 2017 aappddeevv@gmail.com
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package cli

import scala.scalajs.js
import js._
import scala.concurrent.ExecutionContext
import fs2._
import cats._
import cats.data._
import cats.implicits._
import cats.effect._
import io.scalajs.RawOptions
import io.scalajs.nodejs.Error
import io.scalajs.nodejs
import io.scalajs.nodejs.fs.Fs
import monocle.macros.syntax.lens._

import dynamics.cli._
import dynamics.common._
import dynamics.common.implicits._
import MonadlessIO._
import fs2helpers._
import dynamics.client._

import dynamics.etl
import etl._
import etl.sources._
import etl.jsdatahelpers._

import dynamics.client._
import dynamics.http._
import dynamics.client.implicits._
import dynamics.http.implicits._

@js.native
trait CustomerAddress extends js.Object {
  val customeraddressid: String
  val addressnumber: Int
}

/**
  * Loading addresses requires that the input is json with all the
  * attributes set. The only requirement is that parentid is sets
  * in the input data source and it should be remove, along with any other
  * non-modelled attributes using the etl CLI parameters.
  */
class LoadAddressesActions(val context: DynamicsContext) {

  import context._

  val ehandler = ApplicativeError[IO, Throwable]

  /**
    * Create a stream from a json array file source.
    * ...whoops...this does it from MSSQL...change this
    */
  def dbStream(query: String, dbConfigFile: String) = {
    //println(s"query: $query, cfile: $dbConfigFile")
    val cinfo = JSON.parse(Utils.slurp(dbConfigFile))
    MSSQLSource(query, cinfo.asJsObj, 10000)
  }

  def updateAddress(parentId: String, addressNumber: Int, payload: String) = {
    val q = QuerySpec(
      filter = Option(s"addressnumber eq $addressNumber and _parentid_value eq $parentId"),
      select = Seq("customeraddressid", "addressnumber")
    )
    lift {
      val addresses = unlift(dynclient.getList[CustomerAddress](q.url("customeraddresses")))
      if (addresses.length != 1)
        throw new Exception(s"Invalid exising customeraddress entity found for ${parentId}-${addressNumber}")
      unlift(dynclient.update("customeraddresses", addresses(0).customeraddressid, payload))
    }
  }

  def insertAddress(payload: String) = dynclient.createReturnId("/customeraddresses", payload)

  def xform(params: Map[String, String]) =
    jsdatahelpers.stdConverter(params.get("keeps"), params.get("drops"), params.get("renames"))

  /**
    * Assumes parentid holds id. Mutates record.  Not currently used as its
    * assumed that @odata.bind is in the data stream already.
    */
  def parentBind(entityName: String) = (obj: js.Object) => {
    val dic       = obj.asDict[String]
    val id        = dic("parentid")
    val bindKey   = s"parentid_${entityName}@odata.bind"
    val bindValue = s"/${entityName}s($id)"
    dic(bindKey) = bindValue
    obj
  }

  def clean(instring: String): String =
    instring.replaceAllLiterally("\\r\\n", "\\n")

  /**
    * Load source side account "location" correctly. First 2 addresses should map to
    * pre-existing addressnumber 1 and 2 and hence should be updates and
    * not inserts. Data should be sorted by crm accountid then sorted by whatever
    * makes the address you want for 1 and 2 appear at the start of the group.
    *
    * objecttypecode is a string! not a number for this entity: account|contact|...
    */
  val loadAddresses = Action { config =>
    val src: Stream[IO, js.Object] = cats
      .Applicative[Option]
      .map2(config.etl.query orElse config.etl.queryFile.map(Utils.slurp(_)), config.etl.connectionFile)(dbStream _)
      .getOrElse(Stream.empty)
    val counter   = new java.util.concurrent.atomic.AtomicInteger(0)
    val xf        = xform(config.etl.cliParameters)
    val toPayload = (o: js.Object) => clean(xf(o).asJson)
    val program = src
      .take(config.etl.take.getOrElse(Long.MaxValue))
      .drop(config.etl.drop.getOrElse(0))
      .groupBy(jobj => jobj.asDict[String]("parentid"))
      .map { jobj =>
        val id      = jobj._1
        val records = jobj._2
        if (config.etl.verbosity > 0)
          println(s"parentid: ${id}, records: ${records.map(r => Utils.render(r))}")
        counter.getAndAdd(records.length)
        // First two already exist and are updates else inserts
        val updates = records.take(2).zipWithIndex.map { addr =>
          val parentId = addr._1.asDict[String]("parentid")
          val payload  = toPayload(addr._1)
          if (config.etl.verbosity > 2) println(s"Update: parentid=$parentId: ${payload}")
          updateAddress(parentId, 1 + addr._2, payload)
        }
        val inserts = records.drop(2).map { addr =>
          val payload = toPayload(addr)
          if (config.etl.verbosity > 2) println(s"Insert: ${payload}")
          insertAddress(payload)
        }
        (updates ++ inserts).toList.sequence
      }
      .map(Stream.eval(_))

    IO(println("Loading addresses"))
      .flatMap { _ =>
        program.join(config.common.concurrency).run
      }
      .flatMap { _ =>
        IO(println(s"# records loaded: ${counter.get()}"))
      }
  }

}
