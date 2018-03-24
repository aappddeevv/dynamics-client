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

object CSVStringifyX {

  @js.native
  trait CSVStringify extends IEventEmitter {

    /** Streaming version. */
    def apply(): Stringifier = js.native

    /** Streaming version. */
    def apply(options: StringifyOptions | RawOptions): Stringifier = js.native

    /** Callback version. */
    def apply(data: js.Array[js.Array[String]], callback: js.Function2[nodejs.Error, String, js.Any]): Unit = js.native

    /** Callback version.*/
    def apply(data: js.Array[js.Array[String]],
              options: StringifyOptions | RawOptions,
              callback: js.Function2[nodejs.Error, String, js.Any]): Unit = js.native
  }

  /** Allows you to register for events. */
  @js.native
  class Stringifier() extends Readable with Writable {
    def write(data: js.Array[_]): Boolean = js.native
    def write(data: js.Object): Boolean   = js.native
  }

  implicit class StringifierEvents(val s: Stringifier) extends AnyVal {
    @inline
    def onError(listener: Error => Any): s.type = s.on("error", listener)
    @inline
    def onFinish(listener: () => Any): s.type = s.on("finish", listener)
    @inline
    def onReadable(listener: () => Any): s.type = s.on("readable", listener)
    @inline
    def onRecord(listener: (js.Array[js.Any], Int) => Any): s.type = s.on("record", listener)
  }

  @js.native
  @JSImport("csv-stringify", JSImport.Namespace)
  object CSVStringify extends CSVStringify

  class StringifyOptions(
      val columns: js.UndefOr[js.Dictionary[String] | js.Array[String]] = js.undefined,
      val delimiter: js.UndefOr[String] = js.undefined,
      val escape: js.UndefOr[String] = js.undefined,
      val eof: js.UndefOr[Boolean] = js.undefined,
      val header: js.UndefOr[Boolean] = js.undefined,
      val quote: js.UndefOr[String] = js.undefined,
      val quoted: js.UndefOr[Boolean] = js.undefined,
      val quotedEmpty: js.UndefOr[Boolean] = js.undefined,
      val quotedString: js.UndefOr[Boolean] = js.undefined,
      val rowDelimiter: js.UndefOr[Boolean] = js.undefined,
      val formatters: js.UndefOr[js.Dictionary[js.Function1[js.Any, String]]] = js.undefined
  ) extends js.Object
}

class EntityActions(context: DynamicsContext) extends LazyLogger {

  import CSVStringifyX._
  import context._
  implicit val decoder = JSONDecoder

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
    val m           = new MetadataCache(context)
    val concurrency = config.common.concurrency

    val deleteFromCLI: Stream[IO, (String, Long)] =
      if (config.export.query.size > 0)
        Stream
          .eval(m.getEntityDefinition3(config.export.entity))
          .flatMap(ed => deleteFromQuery(config.export.query, ed.PrimaryId, ed.EntitySetName, concurrency))
      else
        Stream.empty

    val deleteFromCSVFile: Stream[IO, (String, Long)] =
      config.export.queryFile
        .map(
          f =>
            etl.sources
              .CSVFileSource(f)
              .map(obj => { val dict = obj.asDict[String]; (dict("entity"), dict("query")) })
              .evalMap(p => m.getEntityDefinition3(p._1).map(ed => (p._1, p._2, ed)))
              .flatMap(t => deleteFromQuery(t._2, t._3.PrimaryId, t._3.EntitySetName, concurrency)))
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
      prefers = OData.PreferOptions(maxPageSize = config.export.maxPageSize,
                                    includeFormattedValues = Some(config.export.includeFormattedValues)))
    val values =
      dynclient
        .getListStream[js.Object](config.export.query, opts)
        .drop(config.export.skip.map(_.toLong).getOrElse(0))

    Stream
      .bracket(IO(Fs.createWriteStream(outputpath, null)))(
        f => {
          if (config.export.wrap) f.write("[")
          values
            .map(Utils.render(_))
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
      prefers = OData.PreferOptions(maxPageSize = config.export.maxPageSize,
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
    val m = new MetadataCache(context)
    val entityList = Stream
      .eval(m.getEntityList())
      .flatMap(Stream.emits[EntityDescription])
      .filter(entity => entityNames.contains(entity.LogicalName))
      .map(entity => (entity.LogicalCollectionName, entity.PrimaryId))

    entityList.map(p => mkCountingStreamForEntity(p._1, p._2))
  }

  def getCountFromFunctionForEntity(entitySet: Seq[String] = Nil) = {
    val collection = s"""EntityNames=[${entitySet.map("'"+_+"'").mkString(",")}]"""
    val request = HttpRequest(Method.GET, s"/RetrieveTotalRecordCount($collection)")
    dynclient.http.expect[RetrieveTotalRecordCountResponse](request)(
      http.instances.entityDecoder.JsObjectDecoder[RetrieveTotalRecordCountResponse])
  }

  def fromFunction(entityNames: Seq[String]): Stream[IO, Stream[IO, (String, Long)]] = {
    Stream.eval(getCountFromFunctionForEntity(entityNames)
      .map{ resp =>
        //js.Dynamic.global.console.log("content from cname", resp)
        val counts = resp.EntityRecordCountCollection
        val rvals = (0 until counts.Count).map(i => (counts.Keys(i), counts.Values(i).toLong))
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
    fromMap(Utils.slurpAsJson[js.Object](file).asDict[String].toMap)

  /** Convert to function: RetrieveTotalRecordCount if possible. */
  def count() = Action { config =>
    val countersQueries  = fromMap(config.export.queries)
    val countersEntity   =
      if(config.export.useFunction) fromFunction(config.common.filter)
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
