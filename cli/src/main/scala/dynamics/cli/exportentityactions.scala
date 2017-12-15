// Copyright (c) 2017 aappddeevv@gmail.com
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package cli

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
      select = config.export.exportSelect,
      filter = config.export.exportFilter,
      top = config.export.exportTop,
      orderBy = config.export.exportOrderBy,
      includeCount = config.export.exportIncludeCount
    )

    if (config.export.exportRaw)
      exportOne(config, query)
    else
      exportAll(config, query)
  }

  def deleteByQuery() = Action { config =>
    val m                                 = new MetadataCache(context)
    val counter                           = new java.util.concurrent.atomic.AtomicInteger(0)
    def deleteOne(es: String, id: String) = dynclient.delete(es, Id(id))

    println(s"Deleting entities using query: ${config.export.entityQuery}")
    val edef = m.getEntityDefinition3(config.export.exportEntity)

    val deletes: Stream[IO, Stream[IO, IO[(DynamicsId, Boolean)]]] =
      Stream.eval(edef).flatMap { ed =>
        dynclient
          .getListStream[js.Object](config.export.entityQuery)
          .map(jsobj => jsobj.asDict[String](ed.PrimaryId))
          .map { a => counter.getAndIncrement(); a }
          .map(id => deleteOne(ed.LogicalCollectionName, id))
          .map(Stream.emit(_))
      }

    deletes.join(config.common.concurrency)
      .run
      .flatMap(_ => IO { println(s"Deleted ${counter.get()} records.") })
  }

  def exportFromQuery() = Action { config =>
    println(s"Export entity using query: ${config.export.entityQuery}")
    val outputpath = config.common.outputFile.getOrElse(config.export.entityQuery.hashCode() + ".json")
    println(s"Output file: $outputpath")

    val opts = DynamicsOptions(
      prefers = OData.PreferOptions(maxPageSize = config.export.exportMaxPageSize,
        includeFormattedValues = Some(config.export.exportIncludeFormattedValues)))
    val values =
      dynclient
        .getListStream[js.Object](config.export.entityQuery, opts)
        .drop(config.export.exportSkip.map(_.toLong).getOrElse(0))

    Stream
      .bracket(IO(Fs.createWriteStream(outputpath, null)))(
        f => {
          if (config.export.exportWrap) f.write("[")
          values
            .map(Utils.render(_))
            .to(_ map { jstr => f.write(jstr + (if (config.export.exportWrap) ",\n" else "\n"))})
        },
        f => IO(if (config.export.exportWrap) f.write("]")).flatMap(_ => f.endFuture().toIO)
      )
      .run
  }

  def exportAll(config: AppConfig, q: QuerySpec): IO[Unit] = {
    println(s"Export entity: ${config.export.exportEntity}")
    val url = q.url(config.export.exportEntity)
    val opts = DynamicsOptions(
      prefers = OData.PreferOptions(maxPageSize = config.export.exportMaxPageSize,
                                    includeFormattedValues = Some(config.export.exportIncludeFormattedValues)))

    val values = dynclient.getListStream[js.Object](url, opts).drop(config.export.exportSkip.map(_.toLong).getOrElse(0))

    // either explicit from CLI or retrieve all of them by getting one record
    val columns =
      (
        if (config.export.exportSelect.size > 0) IO.pure(config.export.exportSelect)
        else getAllColumnNames(config, q)
      ).map(allcols => allcols.map(n => (n, n)))

    val outputpath = config.common.outputFile.getOrElse(s"${config.export.exportEntity}.csv")

    println(s"Output path: $outputpath")
    // TODO, resource control the file descriptor
    val f = Fs.openSync(outputpath, "w")

    def mkStreamer() =
      columns.map { cols =>
        logger.debug(s"Columns: $cols")
        val soptions =
          new StringifyOptions(columns = js.Dictionary[String](cols: _*), header = true)
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

    withSink.map(_ => () /*Fs.closeSync(f)*/ ).run // build Stream into a Task
  }

  /** Get column names by retrieving a single record. */
  def getAllColumnNames(config: AppConfig, q: QuerySpec): IO[Seq[String]] = {
    val query = q.copy(top = Option(1), select = Nil)
    val url   = query.url(config.export.exportEntity)
    dynclient.getList[js.Object](url).map { records =>
      if (records.size == 0) Nil
      else js.Object.keys(records(0)).toSeq
    }
  }

  def exportOne(config: AppConfig, q: QuerySpec): IO[Unit] = {
    val query = q.copy(top = Option(1), select = Nil)
    val url   = query.url(config.export.exportEntity)
    IO(println(s"Dump one record in simple key-value format: ${config.export.exportEntity}")).flatMap { _ =>
      dynclient.getList[js.Object](url).map { records =>
        records.foreach(r => println(s"${PrettyJson.render(r)}"))
        ()
      }
    }
  }

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

  /** Count entities. TODO: Use batch mode. */
  def count(): Action = Kleisli { config =>
    import metadata._
    val m = new MetadataCache(context)
    def mkCountingStream(e: String, pk: String) =
      dynclient
        .getListStream[js.Object](s"/${e}?$$select=${pk}")
        .as(1)
        //.sum
        .fold(0)(_ + _)
        .map(count => (e, count))

    val entityList = Stream
      .eval(m.getEntityList())
      .flatMap(Stream.emits[EntityDescription])
      .filter{entity => config.common.filter.contains(entity.LogicalName) }
      .map(entity => (entity.LogicalCollectionName, entity.PrimaryId))

    val counters = //Stream(("contacts",  "contactid")).
      entityList.map(p => mkCountingStream(p._1, p._2))

    val runCounts = counters.join(config.common.concurrency)
      .runLog
      .map(_.sortBy(_._1))
      .flatMap(results => IO { results.foreach(p => println(s"${p._1}, ${p._2}")) })

    if (config.export.exportRepeat) Stream.eval(runCounts).repeat.run
    else Stream.eval(runCounts).run
  }

}
