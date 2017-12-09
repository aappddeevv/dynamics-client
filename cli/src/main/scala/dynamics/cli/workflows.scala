// Copyright (c) 2017 aappddeevv@gmail.com
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package cli

import scala.scalajs.js
import js._
import js.annotation._
import JSConverters._
import js.Dynamic.{literal => jsobj}
import scala.concurrent._
import io.scalajs.npm.chalk._
import fs2._
import cats._
import cats.data._
import cats.implicits._
import fs2.interop.cats._

import dynamics.common._
import MonadlessTask._
import dynamics.http._
import dynamics.client._
import dynamics.client.implicits._

trait WorkflowJson extends js.Object {
  val workflowid: UndefOr[String]  = js.undefined
  val name: UndefOr[String]        = js.undefined
  val uniquename: UndefOr[String]  = js.undefined
  val description: UndefOr[String] = js.undefined

  val statecode: UndefOr[Int]                  = js.undefined
  val statuscode: UndefOr[Int]                 = js.undefined
  val mode: UndefOr[Int]                       = js.undefined
  val ondemand: UndefOr[Int]                   = js.undefined
  val solutionid: UndefOr[String]              = js.undefined
  val category: UndefOr[Int]                   = js.undefined
  val componentstate: UndefOr[Int]             = js.undefined
  val _activeworkflowid_value: UndefOr[String] = js.undefined
  @JSName("type")
  val wtype: UndefOr[Int] = js.undefined

  val _solution_id_value: UndefOr[String] = js.undefined
}

class ChangeWorkflowStatus(
    val workflowid: String,
    val statecode: Int,
    val statuscode: Int
) extends js.Object

object WorkflowStateCode {
  val Draft     = 0
  val Activated = 1
}

object WorkflowStatusCode {
  val Draft     = 1
  val Activated = 2
}

/**
  * https://msdn.microsoft.com/en-us/library/mt622427.aspx
  */
class WorkflowActions(val context: DynamicsContext) {

  import WorkflowActions._
  import context._
  import dynamics.common.implicits._

  implicit val dec = EntityDecoder.JsObjectDecoder[WorkflowJson]

  protected def getList(attrs: Seq[String] = Nil) = {
    val q = QuerySpec(select = attrs)
    dynclient.getList[WorkflowJson](q.url("workflows"))
  }

  protected def getListStream(attrs: Seq[String] = Nil): Stream[Task, WorkflowJson] = {
    val q = QuerySpec(select = attrs)
    dynclient.getListStream[WorkflowJson](q.url("workflows"))
  }

  protected def filter(r: Traversable[WorkflowJson], filter: Seq[String]) =
    Utils.filterForMatches(r.map(a => (a, Seq(a.name.get, a.description.get))), filter)

  /** Get a single system job by its id. */
  def getById(id: String): Task[WorkflowJson] = {
    dynclient.getOneWithKey[WorkflowJson]("workflows", id)
  }

  def getByName(name: String): Task[WorkflowJson] = {
    val q = QuerySpec(
      filter = Some(s"name eq '$name' and category eq 0")
    )
    dynclient.getList[WorkflowJson](q.url("workflows")).flatMap { r =>
      if (r.size == 0) return Task.now(r(0))
      else Task.fail(new IllegalArgumentException("More than one workflow has that name"))
    }
  }

  def list() = Action { config =>
    println("Workflows. See https://msdn.microsoft.com/en-us/library/mt622427.aspx for value definitions.")
    val cols  = jsobj("8" -> jsobj(width = 40))
    val topts = new TableOptions(border = Table.getBorderCharacters(config.tableFormat), columns = cols)

    lift {
      val res      = unlift(getList())
      val filtered = filter(res, config.filter)
      val data =
        Seq(
          Seq("#",
              "workflowid",
              "name",
              "statecode",
              "statuscode",
              "category",
              "componentstate",
              "type",
              "currentactivation",
              "solutionid",
              "description").map(Chalk.bold(_))) ++
          filtered.zipWithIndex.map {
            case (i, idx) =>
              Seq(
                (idx + 1).toString,
                i.workflowid.orEmpty,
                i.name.orEmpty,
                i.statecode.map(_.toString).orEmpty,
                i.statuscode.map(_.toString).orEmpty,
                i.category.map(_.toString).orEmpty,
                i.componentstate.map(_.toString).orEmpty,
                i.wtype.map(_.toString).orEmpty,
                i._activeworkflowid_value.orEmpty,
                i._solution_id_value.orEmpty,
                i.description.orEmpty
              )
          }
      val out = Table.table(data.map(_.toJSArray).toJSArray, topts)
      println(out)
    }
  }

  def changeActivation() = Action { config =>
    import dynamics.etl._
    val updater = new UpdateProcessor(context)
    val updateone =
      dynclient.update("workflows", _: String, _: String, config.upsertPreventCreate, config.upsertPreventUpdate)
    val newStateCode  = if (config.workflowActivate) WorkflowStateCode.Activated else WorkflowStateCode.Draft
    val newStatusCode = if (config.workflowActivate) WorkflowStatusCode.Activated else WorkflowStatusCode.Draft

    val sourceFromIds = Stream.emits(config.workflowIds)

    val runme = sourceFromIds
      .evalMap(getById)
      .map { w =>
        if (w.statecode == newStateCode) {
          println(s"[${w.name}] is already in the desired activation state.")
          (true, w)
        } else (false, w)
      }
      .collect {
        case p if (!p._1) => p._2
      }
      .map(w => (w.workflowid.get, w.workflowid.get))
      .map { p =>
        InputContext[js.Object](new ChangeWorkflowStatus(p._1, newStateCode, newStatusCode), s"Workflow: ${p._2}")
      }
      .map(updater.mkOne(_, "workflowid", updateone))
      .map(Stream.eval(_).map(println))

    concurrent.join(config.concurrency)(runme).run
  }

  /** Execute a workflow against the results of a query. */
  def executeWorkflow() = Action { config =>
    println(s"Execute a workflow against the results of a query: [${config.workflowODataQuery.get}]")
    println(s"This does not pause to wait for the workflow system job to complete.")
    val entities   = dynclient.getListStream[js.Dictionary[String]](config.workflowODataQuery.get)
    val counter    = new java.util.concurrent.atomic.AtomicInteger(0)
    val inputs     = new java.util.concurrent.atomic.AtomicInteger(0)
    val updater    = new UpdateProcessor(context)
    val workflowId = config.workflowIds(0)

    val cache =
      if (config.workflowCache)
        LineCache(config.workflowCacheFilename.getOrElse(config.workflowPkName) + ".workflow.cache")
      else NeverInCache()

    // single shot or batch, batch does not seem to work right now...
    val finalStep: Pipe[Task, (String, Int), Task[String]] =
      if (!config.workflowBatch) {
        _ map { p =>
          val entityId = p._1
          val body     = s"""{ "EntityId": "$entityId" }"""
          val opts     = DynamicsOptions(prefers = OData.QuietPreferOptions)
          dynclient
            .executeAction[String](ExecuteWorkflow, Entity.fromString(body), Some(("workflows", workflowId)), opts)
            .map(_ => s"Executed workflow against $entityId")
        }
      } else {
        val pt1: Pipe[Task, (String, Int), (HttpRequest, String)] =
          _ map { p: (String, Int) =>
            val source  = s"Entity ${p._2}"
            val request = mkExecuteWorkflowRequest(workflowId, p._1)
            (request.copy(path = dynclient.base + request.path), source) // make full URL for odata patch
          }
        val pt2: Pipe[Task, (HttpRequest, String), Task[String]] =
          _.vectorChunkN(config.batchSize).map(updater.mkBatchFromRequests(_))
        pt1 andThen pt2
      }

    val runme = entities
      .through(fs2helpers.log[js.Dictionary[String]] { _ =>
        inputs.getAndIncrement()
      })
      .map(_.get(config.workflowPkName))
      .collect { case Some(id) => id }
      .through(cache.contains)
      .collect { case (false, id) => id }
      .observe(cache.write)
      .map { id =>
        (id, counter.getAndIncrement())
      }
      .through(finalStep)
      .map(Stream.eval(_).map(println))

    concurrent
      .join(config.concurrency)(runme)
      .run
      .flatMap(_ =>
        Task.delay {
          println(s"${inputs.get} input records.")
          if (config.workflowCache) println(s"${cache.stats} (writes, hits) in cache.")
          println(s"${counter.get} workflows initiated.")
      })
  }

  /** Make a request to execute workflow on an entity instance. */
  def mkExecuteWorkflowRequest(workflowId: String, entityId: String) = {
    val body = s"""{ "EntityId": "$entityId" }"""
    val opts = DynamicsOptions(prefers = OData.QuietPreferOptions)
    dynclient.mkExecuteActionRequest(ExecuteWorkflow, Entity.fromString(body), Some(("workflows", workflowId)), opts)
  }

}

object WorkflowActions {
  val ExecuteWorkflow = "Microsoft.Dynamics.CRM.ExecuteWorkflow"
}
