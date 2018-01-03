// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package cli

import scala.scalajs.js
import js._
import scala.concurrent._
import duration._
import fs2._
import cats._
import cats.data._
import cats.effect._

import dynamics.common._
import dynamics.client._

class TestCommand(context: DynamicsContext) {

  import context._
  import dynamics.common.implicits._
  import etl.sources._
  import etl.jsdatahelpers._
  import dynamics.common.syntax.all._

  val data = JSON.parse("""
{
  "entity": "contact",
  "action": "upsert",
  "data": {
    "firstname": "Blah",
    "lastname": "Blah"
  }
}
""")

  def runTest() = Action { config =>
    println("Running test...")

    /*
    val counter = new java.util.concurrent.atomic.AtomicInteger(0)
    val records = CSVFileSource(config.testArg.get, DefaultCSVParserOptions)

    val runme =
      // records.
      Stream.eval(CSVFileSourceBuffer_(config.testArg.get)).
        flatMap(Stream.emits(_)).
        // simulate some task eval
        map{a => val c = counter.getAndIncrement(); Task.delay{println(s"Record $c: $a"); a} }.
        flatMap(Stream.eval(_)).
        // simulate a transform
        map{ jsobj =>
          val d = jsobj.asInstanceOf[js.Dictionary[js.Any]]
          omit(d, "contactid")
        }.
        // another Taks to be eval'd
        map{obj => Task.delay(obj) }.
        map(Stream.eval(_).map(println))

    concurrent.join(config.concurrency)(runme).
      run.
      map(_ => println(s"${counter.get} records processed."))
     */
    // straight read, skipping all the processing...
    // records.
    //   map{a => counter.getAndIncrement(); println(s"${counter.get}") }.
    //   run.
    //   map(_ => println(s"${counter.get} records processed."))

    implicit val c = new ScalaCacheNodeCache(new NodeCache())
    c.put("key", IO(Some(10)), None)
    val x = c.get[IO[Option[Int]]]("key")
    x.foreach(_.foreach(y => println(s"x: $y")))
    //println(s"x: ${x}")

    c.put[Int]("http://blah.com", -1, None)
    val xc = c.get[Int]("http://blah.com")
    xc.foreach { anInt =>
      println(s"test1: $anInt")
    }
    IO.fromFuture(IO(xc)).map(_ => ())

  // val mc = new MetadataCache(context)
  // val t  = mc.getObjectTypeCode("spruce_interviews").map(println)

  // t.flatMap { _ =>
  //   val t2 = Task.fromFuture(c.get[Task[Option[Int]]]("key"))
  //   t2.map(println)
  //}
  /*
    val runme: Stream[Task, Stream[Task, Unit]] =
      Stream(1,2,3,4,5,6,7,8,9,10,11,12,13,14,15).
        covary[Task].
        through(pipe.lift(i => Task.delay(i))).
        vectorChunkN(2).
        through{ (istream: Stream[Task, Vector[Task[Int]]]) =>
          istream.map{ arrayOfTasks =>
            val flipVector: Task[Vector[Int]] = Task.traverse(arrayOfTasks)(identity)
            val str = Stream.eval(flipVector).map(println _)
            fs2.time.sleep(5.seconds).flatMap(_ => str)
          }}
    // should print out 2-value tuples every 5 seconds.
    concurrent.join(2)(runme).run
   */
  /*
    val mc = new MetadataCache(context)
    val t1 = mc.getOptionValues("contacts", "spruce_earlyleave").map { v => println(s"$v")  }
    val t2 = mc.getOptionValues("contacts", "gendercode").map { v =>  println(s"$v") }
    val t3 = mc.getOptionValues("contacts", "spruce_earlyleave").map { v =>  println(s"Redo: $v")  }
    t1.flatMap(_ => t2.flatMap(_ => t3))
   */
  //Task.now(())
  /*
        val dclient = DynamicsClient(client)
        //println(s"data: ${Utils.pprint(data)}")

        val p = js.Promise.resolve(data: js.Dynamic | Thenable[js.Dynamic])
        val t = p.toTask.
          map(x => println(s"Value: ${Utils.pprint(x)}")).flatMap(_ => Task.now(()))

        val punit = js.Promise.resolve((): Unit | Thenable[Unit])
        val tunit = punit.toTask.
          map(_ => println(s"Unit...nothing to print")).flatMap(_ => Task.now(()))

        val pfail = js.Promise.reject(new RuntimeException("JS Error!"))
        val tfail = pfail.toTask.
          attempt.map(x => println(s"Fail: $x")).flatMap(_ => Task.now(()))

        val first = Task.traverse(Seq(t, tunit, tfail))(identity)

        val ptest = js.Promise.resolve(data: js.Dynamic | Thenable[js.Dynamic])
        val ttest = ptest.toTask.
          map { json =>
            val x = json.value.asInstanceOf[ODataResponse[Int]]
            println("HERE")
            x
          }.flatMap(_ => Task.now(()))

        type Resolve[A] = Function1[A|js.Thenable[A], _]
        type Reject = Function1[scala.Any, _]

        val delaytest = new js.Promise({
          (resolve: Resolve[String], reject: Reject) =>
          js.timers.setTimeout(3 seconds){resolve("hello world!"); ()}
        }).
          toTask.
          map(value => println(s"my promise: $value"))

        Task.traverse(Seq(first, ttest, delaytest))(identity).flatMap(_ => Task.now(()))
   */
  }
}
