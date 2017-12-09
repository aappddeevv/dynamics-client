// Copyright (c) 2017 aappddeevv@gmail.com
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package cli

import dynamics.common._
import dynamics.common.implicits._

import scala.scalajs.js
import js._
import js.Dynamic.{literal => jsobj}
import JSConverters._
import scala.concurrent._
import io.scalajs.util.PromiseHelper.Implicits._
import fs2._
import fs2.util._
import cats._
import cats.data._
import cats.implicits._
import fs2.interop.cats._
import MonadlessTask._

import io.scalajs.RawOptions
import io.scalajs.npm.chalk._
import io.scalajs.nodejs.Error
import io.scalajs.nodejs
import io.scalajs.nodejs.buffer.Buffer
import io.scalajs.nodejs.stream.{Readable, Writable}
import io.scalajs.nodejs.events.IEventEmitter
import io.scalajs.util.PromiseHelper.Implicits._
import io.scalajs.nodejs.fs._
import io.scalajs.npm.csvparse._

import fs2helpers._
import dynamics.http._
import Task._
import etl._
import etl.sources._
import dynamics.client._
import dynamics.client.implicits._

/**
  * Can run a generic update operation but requires data in a specific format. The format
  * is a sequence of json objects that are parse and load ready for an update process.
  * The payload should contain the id to be updated or not contain it, in which case
  * the payload can be upserted if specified.
  */
class UpdateActions(val context: DynamicsContext) {

  import context._
  import dynamics.common.syntax.all._
  import dynamics.etl._

  def update(): Kleisli[Task, AppConfig, Unit] =
    Kleisli { config =>
      println("Update records")
      val pkcol = config.updatePKColumnName
      val updateone = dynclient.update(config.updateEntity,
                                       _: String,
                                       _: String,
                                       config.upsertPreventCreate,
                                       config.upsertPreventUpdate)

      val parserOpts = new ParserOptions(
        columns = true,
        skip_empty_lines = true,
        trim = true
      )
      //val records = CSVFileSource(config.updateDataInputCSVFile, parserOpts)
      val records =
        Stream.eval(CSVFileSourceBuffer_(config.etlDataInputFile, DefaultCSVParserOptions)).flatMap(Stream.emits(_))

      val updater = new UpdateProcessor(context)
      val counter = new java.util.concurrent.atomic.AtomicInteger(0)

      val runme =
        records
          .through(DropTake(config.updateDrop.getOrElse(0), config.updateTake.getOrElse(Int.MaxValue)))
          .through(toInput())
          .map { a =>
            counter.getAndIncrement(); a
          }
          .through(log[InputContext[js.Object]] { ic =>
            if (config.debug) println(s"Input: $ic\n${PrettyJson.render(ic.input)}")
          })
          .through(mkPipe(FilterAttributes(config.updateDrops, config.updateRenames, config.updateKeeps)))
          .through(LogTransformResult[js.Object, js.Object]())
          .through(EmitResultDataWithTag[js.Object, js.Object]())
          .map { p =>
            InputContext(p._1, p._2)
          }
          .through(_ map { ic =>
            updater.mkOne(ic, config.updatePKColumnName, updateone)
          })
          .map(Stream.eval(_).map(println))

      concurrent
        .join(config.concurrency)(runme)
        .run
        .flatMap(_ => Task.delay(println(s"${counter.get} input records processed.")))
    }

}

/** Processes CRM entities. All remote action is delayed until
  * the returned effect is run.
  *
  * Add flexible ways to managed errors and return values
  */
abstract class EntityProcessor(entitySet: String, context: DynamicsContext) {
  def mkOne[A](input: InputContext[A]): Task[String]
  def mkBatch[A](inputs: Vector[InputContext[A]]): Task[String]
}

class UpdateProcessor(val context: DynamicsContext) {

  import context._

  /**
    *  Returning None means the pk was not found in the js object. Only handles
    *  single value PKs. Removes pk if found. Mutates input object.
    */
  def findIdAndMkBody(pkcol: String, j: js.Dictionary[String]): Option[(String, String)] = {
    val pk = j.get(pkcol).filterNot(_.isEmpty)
    j -= pkcol // mutating
    pk.map { (_, JSON.stringify(j.asInstanceOf[js.Object])) }
  }

  /*
  def update(input: IndexedSeq[js.Object], pkcol: String,
    update: (String, String) => Task[String], upsertPreventCreate: Boolean = false): Task[String] = {
    if(input.size == 1)
      mkOne(input(0), pkcol, update)
    else
      mkBatch(input, pkcol, update)
  }
   */

  import DynamicsError._

  /** Run an update based on an input js.Object record and print a result.
    * @param source Record source identifier, typically a string like "Record 3".
    * @param pkcol PK attribute name in input record. Must be a single valued PK.
    * @param record js.Object record. The PK will be removed for the update.
    * @param update Function to perform the update: (id, body).
    * @return Task unit (non-failed) whether success or failure of the update.
    */
  def mkOne(input: InputContext[js.Object], pkcol: String, update: (String, String) => Task[String]): Task[String] = {
    //println(s"mkOne: ${input.source}")
    findIdAndMkBody(pkcol, input.input.asInstanceOf[js.Dictionary[String]]) match {
      case (Some((id, body))) =>
        //println(s"DEBUG: $id, $body")
        update(id, body).attempt.map { updateOneResultReporter(input.source, id) apply _ }
      case (None) =>
        Task.now(
          s"${input.source}: No id found in input data. Unable to update.\nInput record: ${PrettyJson.render(input.input)}")
    }
  }

  /** Report out on an update attempt. */
  def updateOneResultReporter(source: String, id: String): PartialFunction[Either[Throwable, String], String] = {
    case Right(v) =>
      s"${source}: Update successful: Entity id $id. [${js.Date()}]"
    case t: Left[DynamicsError, _] =>
      if (t.value.status == Status.NotFound)
        s"${source}: Entity with id $id not found. [${js.Date()}]"
      else {
        s"Error at ${source}: [${js.Date()}]\n" + t.value.show
      }
  }

  /** Run the updates but in batch mode. (id,body) => HttpRequest. */
  def mkBatch(inputs: Vector[InputContext[js.Object]],
              pkcol: String,
              mkRequest: (String, String) => HttpRequest): Task[String] = {
    val requests: Vector[(HttpRequest, String)] = inputs
      .map { input =>
        val data   = input.input
        val source = input.source
        findIdAndMkBody(pkcol, data.asDict[String]) match {
          case Some((id, body)) => Some((mkRequest(id, body), source))
          case _                => println(s"$source: No id found. Record will not be updated."); None
        }
      }
      .collect { case Some(p) => (p._1, p._2) }

    mkBatchFromRequests(requests)
  }

  /** Run the updates but in batch mode. Inputs are: (request, source identifier) */
  def mkBatchFromRequests(requests: Vector[(HttpRequest, String)], useChangeSet: Boolean = true): Task[String] = {
    val reqs: Seq[SinglePart] = requests.map(_._1).map(SinglePart(_))

    val label: String = requests.map(_._2).toList match { // expensive just for a label
      case list @ (head :: tail) =>
        ("batch_" + head + "_" + list.last).replaceAll(" ", "_")
      case _ => "batch_" + java.util.UUID.randomUUID.toString
    }

    val m =
      if (useChangeSet)
        Multipart(ChangeSet(reqs, Boundary.mkBoundary("changeset_")) :: Nil, Boundary(label))
      else
        Multipart(reqs, Boundary(label))

    // Create the "task"
    dynclient.batch[String](HttpRequest(Method.PUT, "/$batch"), m).attempt.map {
      _ match {
        case Right(v) =>
          s"Batch $label (${requests.size} records) processed. [${js.Date()}]"
        case t: Left[DynamicsError, _] =>
          //case Left(t) =>
          s"Error processing batch: $label: [${js.Date()}]\n${t.toString()}\n" + t.value.show
      }
    }
  }

}
