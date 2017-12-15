// Copyright (c) 2017 aappddeevv@gmail.com
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package cli

import scala.scalajs.js
import js._
import js.Dynamic.{literal => jsobj}
import JSConverters._
import io.scalajs.nodejs._
import buffer._
import scala.concurrent._
import io.scalajs.util.PromiseHelper.Implicits._
import io.scalajs.npm.chalk._
import fs2._
import cats._
import cats.data._
import cats.implicits._
import cats.effect._

import dynamics.common._
import dynamics.common.implicits._
import MonadlessIO._
import dynamics.client._
import dynamics.http._
import dynamics.client.implicits._
import dynamics.http.implicits._

class AsyncOperationsCommand(val context: DynamicsContext) {

  import context._

  implicit val dec = JsObjectDecoder[AsyncOperationOData]

  protected def getList(attrs: Seq[String] = Nil) = {
    val q = QuerySpec(select = attrs)
    dynclient.getList[AsyncOperationOData](q.url("asyncoperations"))
  }

  protected def getListStream(attrs: Seq[String] = Nil): Stream[IO, AsyncOperationOData] = {
    val q = QuerySpec(select = attrs)
    dynclient.getListStream[AsyncOperationOData](q.url("asyncoperations"))
  }

  protected def filter(r: Traversable[AsyncOperationOData], filter: Seq[String]) =
    Utils.filterForMatches(r.map(a => (a, Seq(a.name.get))), filter)

  /** Get a single system job by its id. */
  def getById(id: String)(implicit ec: ExecutionContext): IO[AsyncOperationOData] = {
    dynclient.getOneWithKey[AsyncOperationOData]("asyncoperations", id)
  }

  protected def withData(): Kleisli[IO, AppConfig, (AppConfig, Seq[AsyncOperationOData])] =
    Kleisli { config =>
      getList(Seq("asyncoperationid", "name", "statuscode", "operationtype"))
        .map { res =>
          filter(res, config.common.filter)
        }
        .map((config, _))
    }

  protected def _list(): Kleisli[IO, (AppConfig, Seq[AsyncOperationOData]), Unit] =
    Kleisli {
      case (config, res) =>
        IO {
          println("Async Operations")
          val topts = new TableOptions(border = Table.getBorderCharacters(config.common.tableFormat))
          val data =
            Seq(
              Seq("#", "asyncoperationid", "name", "statuscode", "operationtype", "executiontimespan").map(
                Chalk.bold(_))) ++
              res.zipWithIndex.map {
                case (i, idx) =>
                  val statuslabel = AsyncOperation.StatusCodes.get(i.statuscode.get).getOrElse("<no status label>")
                  Seq(
                    idx.toString,
                    i.asyncoperationid.orEmpty,
                    i.name.orEmpty,
                    statuslabel,
                    i.operationtype.map(_.toString).orEmpty,
                    i.executiontimespan.map(_.toString).orEmpty
                  )
              }
          val out = Table.table(data.map(_.toJSArray).toJSArray, topts)
          println(out)
        }
    }

  def list() = withData andThen _list

  /**
    * TODO: Change state to completed in order to delete, that's two calls!
    * "waiting" state does not seem to need this.
    */
  def deleteCompletedStream(statuscode: Int = 30) = Action { config =>
    println("Delete system jobs.")

    val deleteone = (name: String, id: String) =>
      dynclient.delete("asyncoperations", id).flatMap { id =>
        IO(println(s"[${name}] Deleted on ${new Date().toISOString()}."))
    }

    val counter = new java.util.concurrent.atomic.AtomicInteger(0)

    val nAtATime = getListStream(Seq("asyncoperationid", "name", "statuscode"))
      .filter(_.statuscode.get == statuscode)
      .map { item =>
        counter.getAndIncrement(); item
      }
      .vectorChunkN(10)
      .map(vec =>
        Stream.emits(vec) evalMap
          (item => deleteone(item.name.orEmpty, item.asyncoperationid.get)))

    nAtATime.join(5)
      .run
      .map(_ => println(s"${counter.get} records processed."))
  }

  def deleteCompleted(): Action           = deleteCompletedStream(30)
  def deleteCanceled(): Action            = deleteCompletedStream(32)
  def deleteFailed(): Action              = deleteCompletedStream(31)
  def deleteInProgress(): Action          = deleteCompletedStream(20)
  def deleteWaiting(): Action             = deleteCompletedStream(10)
  def deleteWaitingForResources(): Action = deleteCompletedStream(0)

  /**
    * Cancel jobs.
    *
    * Cancel jobs if:
    * Not already cancelled (statecode = 3)
    * Not Locked & Cancelling (statecode=2, statuscode=22)
    */
  def cancel() = Action { config =>
    import dynamics.etl._
    val updater = new UpdateProcessor(context)
    val updateone =
      dynclient.update("asyncoperations",
                       _: String,
                       _: String,
                       config.update.upsertPreventCreate,
                       config.update.upsertPreventUpdate)
    val cancel = 3

    println(s"""Cancel jobs based on name regex ${config.common.filter.mkString(", ")}""")
    val counter = new java.util.concurrent.atomic.AtomicInteger(0)

    val runme = getListStream(Seq("asyncoperationid", "name", "statecode", "statuscode"))
      .filter(job => job.statecode.get != cancel && (job.statecode.get != 2 && job.statuscode.get != 22))
      .flatMap { job =>
        val matches = filter(Seq(job), config.common.filter)
        Stream.emits(matches)
      }
      .map(job =>
        InputContext[js.Object](new ChangeAsyncJobState(job.asyncoperationid.get, cancel), s"Job: [${job.name.get}]"))
      .map(updater.mkOne(_, "asyncoperationid", updateone))
      .map(Stream.eval(_).map(println))

    runme.join(config.common.concurrency).run
  //.flatMap(_ => Task.delay(println(s"${counter.get} records processed.")))
  }

}
