// Copyright (c) 2017 The Trapelo Group LLC
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
import cats.effect._

import dynamics.common._
import MonadlessIO._
import dynamics.http._
import dynamics.client._
import dynamics.client.implicits._
import dynamics.http.implicits._

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

  val _solution_id_value: UndefOr[String]      = js.undefined
  val _parentworkflowid_value: UndefOr[String] = js.undefined

  val _ownerid_value: UndefOr[String] = js.undefined
  @JSName("_ownerid_value@OData.Community.Display.V1.FormattedValue")
  val _ownerid_str: UndefOr[String] = js.undefined
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

  val ehandler = implicitly[MonadError[IO, Throwable]]

  implicit val dec = JsObjectDecoder[WorkflowJson]

  protected def getList(attrs: Seq[String] = Nil) = {
    val q = QuerySpec(select = attrs)
    dynclient.getList[WorkflowJson](q.url("workflows"))
  }

  protected def getListStream(attrs: Seq[String] = Nil): Stream[IO, WorkflowJson] = {
    val q = QuerySpec(select = attrs)
    dynclient.getListStream[WorkflowJson](q.url("workflows"))
  }

  protected def filter(r: Traversable[WorkflowJson], filter: Seq[String]) =
    Utils.filterForMatches(r.map(a => (a, Seq(a.name.get, a.description.get))), filter)

  /** Get a single system job by its id. */
  def getById(id: String): IO[WorkflowJson] = {
    dynclient.getOneWithKey[WorkflowJson]("workflows", id)
  }

  /** Get workflow by parentvalueid, process type = activation, statecode = activated. */
  def getByParentId(parentId: String): IO[Seq[WorkflowJson]] = {
    val q = QuerySpec(filter = Some(s"_parentworkflowid_value eq $parentId and type eq 2 and statecode eq 1"))
    dynclient.getList[WorkflowJson](q.url("workflows"))
  }

  def getByName(name: String): IO[WorkflowJson] = {
    val q = QuerySpec(
      filter = Some(s"name eq '$name' and category eq 0")
    )
    dynclient.getList[WorkflowJson](q.url("workflows")).flatMap { r =>
      if (r.size == 0) return IO.pure(r(0))
      else ehandler.raiseError(new IllegalArgumentException("More than one workflow has that name"))
    }
  }

  def list() = Action { config =>
    println("Workflows. See https://msdn.microsoft.com/en-us/library/mt622427.aspx for value definitions.")
    val cols = jsobj(
      "2"  -> jsobj(width = 30),
      "12" -> jsobj(width = 40)
    )
    val topts = new TableOptions(border = Table.getBorderCharacters(config.common.tableFormat), columns = cols)

    lift {
      val res      = unlift(getList())
      val filtered = filter(res, config.common.filter)
      val data =
        Seq(
          Seq(
            "#",
            "workflowid",
            "name",
            "statecode",
            "statuscode",
            "category",
            "componentstate",
            "type",
            "currentactivation",
            "parentworkflowid",
            "ownerid",
            "solutionid",
            "description"
          ).map(Chalk.bold(_))) ++
          filtered.zipWithIndex.map {
            case (i, idx) =>
              //js.Dynamic.global.console.log(idx, i)
              Seq(
                (idx + 1).toString,
                i.workflowid.orEmpty,
                i.name.orEmpty,
                i.statecode.toOption.flatMap(WorkflowActions.StateCodeByInt.get(_)).getOrElse("?"),
                i.statuscode.toOption.flatMap(WorkflowActions.StatusCodeByInt.get(_)).getOrElse("?"),
                i.category.toOption.flatMap(WorkflowActions.CategoryByInt.get(_)).getOrElse("?"),
                i.componentstate.toOption.flatMap(WorkflowActions.ComponentStateByInt.get(_)).getOrElse("?"),
                i.wtype.toOption.flatMap(WorkflowActions.ProcessTypeByInt.get(_)).getOrElse("?"),
                i._activeworkflowid_value.orEmpty,
                i._parentworkflowid_value.orEmpty,
                i._ownerid_str.orEmpty,
                i._solution_id_value.orEmpty,
                i.description.orEmpty
              )
          }
      val out = Table.table(data.map(_.toJSArray).toJSArray, topts)
      println(out)
    }
  }

  def changeActivation() = Action { config =>
    val (newStateCode, newStatusCode) =
      if (config.workflow.activate) (WorkflowStateCode.Activated, WorkflowStatusCode.Activated)
      else (WorkflowStateCode.Draft, WorkflowStatusCode.Draft)
    val actionName    = if (config.workflow.activate) "Activated" else "Deactivated"
    val sourceFromIds = Stream.emits(config.workflow.workflowIds)
    val runme = sourceFromIds
      .evalMap(getById)
      .map { w =>
        val id      = w.workflowid.get
        val baseUrl = config.common.connectInfo.acquireTokenResource.orEmpty
        val request = WorkflowActions.mkSOAPRequest(baseUrl, "workflow", id, newStateCode, newStatusCode)
        dynclient.http.fetch[String](request) {
          case Status.Successful(response) => IO.pure(s"$actionName $id (${w.name.orEmpty})")
          case failedResponse =>
            ehandler.raiseError(DynamicsClientError(
              s"Unable to change $id (${w.name.orEmpty})",
              None,
              Option(
                UnexpectedStatus(failedResponse.status, request = Some(request), response = Option(failedResponse))),
              failedResponse.status
            ))
        }
      }
      .map(Stream.eval(_).map(println))

    runme.join(config.common.concurrency).compile.drain
  }

  /** Execute a workflow against the results of a query. */
  def executeWorkflow() = Action { config =>
    println(s"Execute a workflow against the results of a query: [${config.workflow.workflowODataQuery.get}]")
    println(s"This does not pause to wait for the workflow system job to complete.")
    val entities   = dynclient.getListStream[js.Dictionary[String]](config.workflow.workflowODataQuery.get)
    val counter    = new java.util.concurrent.atomic.AtomicInteger(0)
    val inputs     = new java.util.concurrent.atomic.AtomicInteger(0)
    val updater    = new UpdateProcessor(context)
    val workflowId = config.workflow.workflowIds(0)

    val cache =
      if (config.workflow.workflowCache)
        LineCache(config.workflow.workflowCacheFilename.getOrElse(config.workflow.workflowPkName) + ".workflow.cache")
      else NeverInCache()

    // single shot or batch, batch does not seem to work right now...
    val finalStep: Pipe[IO, (String, Int), IO[String]] =
      if (!config.common.batch) {
        _ map { p =>
          val entityId = p._1
          val body     = s"""{ "EntityId": "$entityId" }"""
          val opts     = DynamicsOptions(prefers = OData.QuietPreferOptions)
          dynclient
            .executeAction[String](ExecuteWorkflow, Entity.fromString(body), Some(("workflows", workflowId)), opts)
            .map(_ => s"Executed workflow against $entityId")
        }
      } else {
        val pt1: Pipe[IO, (String, Int), (HttpRequest[IO], String)] =
          _ map { p: (String, Int) =>
            val source  = s"${p._1}" // identifier is the id
            val request = mkExecuteWorkflowRequest(workflowId, p._1)
            (request.copy(path = dynclient.base + request.path), source) // make full URL for odata patch
          }
        val pt2: Pipe[IO, (HttpRequest[IO], String), IO[String]] =
          _.vectorChunkN(config.common.batchSize).map { v =>
            val ids = v.map(_._2).mkString(", ")
            updater
              .mkBatchFromRequests(v, false)
              .map(_ => s"Executed batch request against entities: $ids")
          }
        pt1 andThen pt2
      }

    val runme = entities
      .through(fs2helpers.log[js.Dictionary[String]] { _ =>
        inputs.getAndIncrement()
      })
      .map(_.get(config.workflow.workflowPkName))
      .collect { case Some(id) => id }
      .through(cache.contains)
      .collect { case (false, id) => id }
      .observe(cache.write)
      .map { id =>
        (id, counter.getAndIncrement())
      }
      .through(finalStep)
      .map(Stream.eval(_).map(println))

    runme
      .join(config.common.concurrency)
      .compile
      .drain
      .flatMap(_ =>
        IO {
          println(s"${inputs.get} input records.")
          if (config.workflow.workflowCache) println(s"${cache.stats} (writes, hits) in cache.")
          println(s"${counter.get} workflows initiated.")
      })
  }

  /** Make a request to execute workflow on an entity instance. */
  def mkExecuteWorkflowRequest(workflowId: String, entityId: String) = {
    val body = s"""{ "EntityId": "$entityId" }"""
    val opts = DynamicsOptions(prefers = OData.QuietPreferOptions)
    dynclient.mkExecuteActionRequest(ExecuteWorkflow, Entity.fromString(body), Some(("workflows", workflowId)), opts)
  }

  def get(command: String): Action = {
    command match {
      case "list"             => list()
      case "execute"          => executeWorkflow()
      case "changeactivation" => changeActivation()
      case _ =>
        Action { _ =>
          IO(println(s"workflows command '${command}' not recognized."))
        }
    }
  }

}

object WorkflowActions {
  val ExecuteWorkflow = "Microsoft.Dynamics.CRM.ExecuteWorkflow"

  val ComponentStateByInt = Map[Int, String](
    0 -> "Published",
    1 -> "Unpublished",
    2 -> "Deleted",
    3 -> "Deleted Unpublished"
  )

  val StateCodeByInt = Map[Int, String](
    0 -> "Draft",
    1 -> "Activated"
  )

  val StatusCodeByInt = Map[Int, String](
    1 -> "Draft",
    2 -> "Activated"
  )

  val CategoryByInt = Map[Int, String](
    0 -> "Workflow",
    1 -> "Dialog",
    2 -> "Business Rule",
    3 -> "Action",
    4 -> "Business Process Flow"
  )

  val ProcessTypeByInt = Map[Int, String](
    1 -> "Definition",
    2 -> "Activation",
    3 -> "Template"
  )

  def mkSOAPRequest(baseUrl: String, ENAME: String, ID: String, STATE: Int, STATUS: Int) = {
    val body = setStateRequest(ENAME, ID, STATE, STATUS)
    val headers = HttpHeaders(
      "Content-Type" -> "text/xml; charset=utf-8",
      "SOAPAction"   -> "http://schemas.microsoft.com/xrm/2011/Contracts/Services/IOrganizationService/Execute",
      "Accept"       -> "application/xml"
    )
    HttpRequest[IO](Method.POST, s"$baseUrl/$SOAPEP", headers = headers, body = Entity.fromString(body))
  }

  val SOAPEP = "XRMServices/2011/Organization.svc/web"

  /** Use singular logical name, like contact or account, not contacts or accounts. */
  def setStateRequest(ENAME: String, ID: String, STATE: Int, STATUS: Int) =
    s"""<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
               <s:Body>
               <Execute xmlns="http://schemas.microsoft.com/xrm/2011/Contracts/Services" xmlns:i="http://www.w3.org/2001/XMLSchema-instance">
                     <request i:type="b:SetStateRequest" xmlns:a="http://schemas.microsoft.com/xrm/2011/Contracts" xmlns:b="http://schemas.microsoft.com/crm/2011/Contracts">
                     <a:Parameters xmlns:c="http://schemas.datacontract.org/2004/07/System.Collections.Generic">
                       <a:KeyValuePairOfstringanyType>
                      <c:key>EntityMoniker</c:key>
                     <c:value i:type="a:EntityReference">
                    <a:Id>${ID}</a:Id>
                     <a:LogicalName>${ENAME}</a:LogicalName>
                   <a:Name i:nil="true" />
                 </c:value>
                </a:KeyValuePairOfstringanyType>
                     <a:KeyValuePairOfstringanyType>
                    <c:key>State</c:key>
                     <c:value i:type="a:OptionSetValue">
                    <a:Value>${STATE}</a:Value>
                   </c:value>
                </a:KeyValuePairOfstringanyType>
                 <a:KeyValuePairOfstringanyType>
                 <c:key>Status</c:key>
                <c:value i:type="a:OptionSetValue">
                   <a:Value>${STATUS}</a:Value>
                   </c:value>
                  </a:KeyValuePairOfstringanyType>
                   </a:Parameters>
                <a:RequestId i:nil="true" />
                <a:RequestName>SetState</a:RequestName>
                </request>
                 </Execute>
                 </s:Body>
               </s:Envelope>
"""
}
