// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package cli

import scala.concurrent.duration._
import scala.scalajs.js
import js._
import js.annotation._
import js.Dynamic.{literal => jsobj}
import js.JSConverters._
import scala.concurrent._
import io.scalajs.RawOptions
import io.scalajs.npm.chalk._
import io.scalajs.nodejs.Error
import io.scalajs.nodejs
import io.scalajs.nodejs.buffer.Buffer
import io.scalajs.nodejs.stream.{Readable, Writable}
import io.scalajs.nodejs.events.IEventEmitter
import io.scalajs.util.PromiseHelper.Implicits._
import io.scalajs.nodejs.fs
import io.scalajs.nodejs.fs._
import fs2._
import cats._
import cats.data._
import cats.implicits._
import cats.effect._

import dynamics.common._
import dynamics.common.implicits._
import dynamics.client._
import dynamics.http._
import dynamics.client.syntax.queryspec._
import dynamics.http.implicits._
import dynamics.client.common._

class EntityActions(context: DynamicsContext) extends LazyLogger {

  import CSVStringifyX._
  import context._
  implicit val decoder = JSONDecoder()

  def export() = Action { config =>
    val query = QuerySpec(
      select = config.export.select,
      filter = config.export.filter,
      top = config.export.top,
      orderBy = config.export.orderBy,
      includeCount = config.export.includeCount
    )

    if (config.export.raw)
      exportOne(config, query)
    else
      exportAll(config, query)
  }

  /** Return a query that deletes and returns 1 for each delete. */
  def deleteFromQuery(query: String, primaryId: String, esname: String, concurrency: Int) = {
    dynclient
      .getListStream[js.Object](query)
      .map(jsobj => jsobj.asDict[String](primaryId))
      .map(id => dynclient.delete(esname, Id(id)))
      .map(Stream.eval(_))
      .join(concurrency)
      .as(1L)
      .fold(0L)(_ + _)
      .map(count => (query, count))
  }

  // TODO: Convert to batch request, this is way stupid.
  def deleteByQuery() = Action { config =>
    val m           = new MetadataCache(dynclient, LCID)
    val concurrency = config.common.concurrency

    val deleteFromCLI: Stream[IO, (String, Long)] =
      if (config.export.query.size > 0)
        Stream
          .eval(m.entityByName(config.export.entity))
          .collect { case Some(ed) => ed }
          .flatMap(ed => deleteFromQuery(config.export.query, ed.PrimaryIdAttribute, ed.EntitySetName, concurrency))
      else
        Stream.empty

    val deleteFromCSVFile: Stream[IO, (String, Long)] =
      config.export.queryFile
        .map(
          f =>
            etl.sources
              .CSVFileSource(f)
              .map(obj => { val dict = obj.asDict[String]; (dict("entity"), dict("query")) })
              .evalMap(p => m.entityByName(p._1).map(_.map((p._1, p._2, _))))
              .collect { case Some(e) => e }
              .flatMap(t => deleteFromQuery(t._2, t._3.PrimaryIdAttribute, t._3.EntitySetName, concurrency)))
        .getOrElse(Stream.empty)

    (deleteFromCLI ++ deleteFromCSVFile)
      .map(p => println(s"${p._2} deletes: ${p._1}"))
      .compile
      .drain
  }

  def exportFromQuery() = Action { config =>
    println(s"Export entity using query: ${config.export.query}")
    val outputpath = config.common.outputFile.getOrElse("dump_" + config.export.query.hashCode() + ".json")
    println(s"Output file: $outputpath")

    val opts = DynamicsOptions(
      prefers =
        client.common.headers.PreferOptions(maxPageSize = config.export.maxPageSize,
                                            includeFormattedValues = Some(config.export.includeFormattedValues)))
    val values =
      dynclient
        .getListStream[js.Object](config.export.query, opts)
        .drop(config.export.skip.map(_.toLong).getOrElse(0))
        .take(config.export.top.map(_.toLong).getOrElse(Long.MaxValue))

    Stream
      .bracket(IO(Fs.createWriteStream(outputpath, null)))(
        f => {
          if (config.export.wrap) f.write("[")
          values
            .map(JSON.stringify(_))
            //.map(Utils.render(_))
            .to(_ map { jstr =>
              f.write(jstr + (if (config.export.wrap) ",\n" else "\n"))
            })
        },
        f => IO(if (config.export.wrap) f.write("]")).flatMap(_ => f.endFuture().toIO)
      )
      .compile
      .drain
  }

  def exportAll(config: AppConfig, q: QuerySpec): IO[Unit] = {
    println(s"Export entity: ${config.export.entity}")
    val url = q.url(config.export.entity)
    val opts = DynamicsOptions(
      prefers =
        client.common.headers.PreferOptions(maxPageSize = config.export.maxPageSize,
                                            includeFormattedValues = Some(config.export.includeFormattedValues)))

    val values = dynclient.getListStream[js.Object](url, opts).drop(config.export.skip.map(_.toLong).getOrElse(0))

    // either explicit from CLI or retrieve all of them by getting one record
    val columns =
      (
        if (config.export.select.size > 0) IO.pure(config.export.select)
        else getAllColumnNames(config, q)
      ).map(allcols => allcols.map(n => (n, n)))

    val outputpath = config.common.outputFile.getOrElse(s"${config.export.entity}.csv")

    println(s"Output path: $outputpath")
    // TODO, resource control the file descriptor
    val f = Fs.openSync(outputpath, "w")

    def mkStreamer() =
      columns.map { cols =>
        logger.debug(s"Columns: $cols")
        val soptions =
          new StringifyOptions(
            columns = js.Dictionary[String](cols: _*),
            header = config.export.header
          )
        CSVStringify(soptions)
      }

    val withSink = Stream.bracket(mkStreamer())(
      streamer => {
        import Readable._
        streamer.onReadable(() => {
          // must read chunks of the string from Readable
          streamer.iterator[Buffer].filter(_ != null).foreach { b =>
            val out = b.toString()
            Fs.writeSync(f, out)
          //println(s"item: buffer=$b, out=$out, isbuffer=${Buffer.isBuffer(b)}")
          }
        })
        values.to(_ map { jobj =>
          streamer.write(jobj)
          () // sink sign is to return unit
        })
      },
      streamer => IO(streamer.end())
    )

    withSink.map(_ => () /*Fs.closeSync(f)*/ ).compile.drain // build Stream into a Task
  }

  /** Get column names by retrieving a single record. */
  def getAllColumnNames(config: AppConfig, q: QuerySpec): IO[Seq[String]] = {
    val query = q.copy(top = Option(1), select = Nil)
    val url   = query.url(config.export.entity)
    dynclient.getList[js.Object](url).map { records =>
      if (records.size == 0) Nil
      else js.Object.keys(records(0)).toSeq
    }
  }

  def exportOne(config: AppConfig, q: QuerySpec): IO[Unit] = {
    val query = q.copy(top = Option(1), select = Nil)
    val url   = query.url(config.export.entity)
    IO(println(s"Dump one record in simple key-value format: ${config.export.entity}")).flatMap { _ =>
      dynclient.getList[js.Object](url).map { records =>
        records.foreach(r => println(s"${PrettyJson.render(r)}"))
        ()
      }
    }
  }

  /*
  private def testit() = {
    println("TEST START")
    val str = CSVStringify(Array(Array("1", "2", "3"), Array("4", "5", "6")), (err, result) => {
      println(s"stringify result: $result")
    })

    val streamer = CSVStringify(
      new StringifyOptions(
        columns = js.Dictionary("0" -> "a", "1" -> "b", "2" -> "c"),
        header = true
      ))
    streamer.onReadable(() => {
      println(s"item: ${streamer.read()}")
    })
    streamer.onRecord((a, i) => {
      println(s"get record: $a @ $i")
    })
    streamer.write(Array("1", "2", "3"))
    streamer.write(Array("4", "5", "6"))
    streamer.write(jsobj("0" -> "7", "b" -> "8", "a" -> "9"))
    streamer.end()
    println("TEST END")
  }
   */

  def mkCountingStreamForEntity(e: String, pk: String) =
    dynclient
      .getListStream[js.Object](s"/${e}?$$select=${pk}")
      .as(1L)
      .fold(0L)(_ + _)
      .map(count => (e, count))

  def fromEntityNames(entityNames: Seq[String]): Stream[IO, Stream[IO, (String, Long)]] = {
    import metadata._
    val m = new MetadataCache(context.dynclient, context.LCID)
    val entityList = Stream
      .eval(m.entityDefinitions)
      .flatMap(Stream.emits[EntityDefinition])
      .filter(entity => entityNames.contains(entity.LogicalName))
      .map(entity => (entity.LogicalCollectionName, entity.PrimaryIdAttribute))

    entityList.map(p => mkCountingStreamForEntity(p._1, p._2))
  }

  def getCountFromFunctionForEntity(entitySet: Seq[String] = Nil) = {
    val collection = s"""EntityNames=[${entitySet.map("'" + _ + "'").mkString(",")}]"""
    val request    = HttpRequest[IO](Method.GET, s"/RetrieveTotalRecordCount($collection)")
    dynclient.http.expect[RetrieveTotalRecordCountResponse](request)(
      http.instances.entitydecoder.JsObjectDecoder[RetrieveTotalRecordCountResponse]())
  }

  def fromFunction(entityNames: Seq[String]): Stream[IO, Stream[IO, (String, Long)]] = {
    Stream.eval(
      getCountFromFunctionForEntity(entityNames)
        .map { resp =>
          //js.Dynamic.global.console.log("content from cname", resp)
          val counts = resp.EntityRecordCountCollection
          val rvals  = (0 until counts.Count).map(i => (counts.Keys(i), counts.Values(i).toLong))
          Stream.emits(rvals).covary[IO]
        })
  }

  def fromMap(queries: Map[String, String]) = {
    Stream
      .emits(queries.toSeq)
      .map(
        p =>
          dynclient
            .getListStream[js.Object](p._2)
            .as(1L)
            .fold(0L)(_ + _)
            .flatMap(count => Stream.emit((p._1, count)).covary[IO]))
  }

  def fromJsonFile(file: String) =
    fromMap(IOUtils.slurpAsJson[js.Object](file).asDict[String].toMap)

  /** Convert to function: RetrieveTotalRecordCount if possible. */
  def count() = Action { config =>
    val countersQueries = fromMap(config.export.queries)
    val countersEntity =
      if (config.export.useFunction) fromFunction(config.common.filter)
      else fromEntityNames(config.common.filter)
    val countersFromJson = config.export.queryFile.map(fromJsonFile(_)).getOrElse(Stream.empty)
    val all              = countersQueries ++ countersEntity ++ countersFromJson
    val runCounts = all
      .join(config.common.concurrency)
      .compile
      .toVector
      .map(_.sortBy(_._1))
      .flatMap(results => IO { results.foreach(p => println(s"${p._1}, ${p._2}")) })

    if (config.export.repeat)
      (Stream.eval(runCounts) ++ sch.sleep_[IO](config.export.repeatDelay seconds)).repeat.compile.drain
    else
      Stream.eval(runCounts).compile.drain
  }

}
