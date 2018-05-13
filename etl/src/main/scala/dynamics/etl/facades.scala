// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package etl

import scala.scalajs.js
import js.UndefOr
import js.|
import js.annotation._

import io.scalajs.nodejs._
import io.scalajs.nodejs.fs._
import scala.concurrent.Future
import io.scalajs.RawOptions
import io.scalajs.nodejs.events.IEventEmitter

class MSSQLOptions(
    val encrypet: js.UndefOr[Boolean] = js.undefined
) extends js.Object

class MSSQLConfig(
    val user: js.UndefOr[String] = js.undefined,
    val password: js.UndefOr[String] = js.undefined,
    val server: js.UndefOr[String] = js.undefined,
    val database: js.UndefOr[String] = js.undefined,
    val port: js.UndefOr[Int] = js.undefined,
    val domain: js.UndefOr[String] = js.undefined,
    val connectionTimeout: js.UndefOr[Int] = js.undefined,
    val requestTimeout: js.UndefOr[Int] = js.undefined,
    val parseJSON: js.UndefOr[Boolean] = js.undefined,
    val stream: js.UndefOr[Boolean] = js.undefined,
    val pool: js.UndefOr[PoolOptions] = js.undefined,
    val options: js.UndefOr[MSSQLOptions | RawOptions] = js.undefined
) extends js.Object

class PoolOptions(
    val max: js.UndefOr[Int] = js.undefined,
    val min: js.UndefOr[Int] = js.undefined,
    val idleTimeoutMillis: js.UndefOr[Int] = js.undefined,
    val acquireTimeoutMillis: js.UndefOr[Int] = js.undefined,
    val fifo: js.UndefOr[Boolean] = js.undefined,
    val priorityRange: js.UndefOr[Int] = js.undefined,
    val autostart: js.UndefOr[Boolean] = js.undefined
    // ...more should go here...
) extends js.Object

@js.native
@JSImport("mssql", JSImport.Namespace)
object MSSQL extends js.Object with IEventEmitter {
  def connect(config: js.Dynamic | RawOptions | String | MSSQLConfig): js.Promise[ConnectionPool] = js.native
  def Request(): Request                                                                          = js.native
  def ConnectionPool(options: js.UndefOr[PoolOptions | RawOptions]): ConnectionPool               = js.native
}

@js.native
trait ConnectionPool extends js.Object with IEventEmitter {
  def request(): Request                    = js.native
  def close(): js.Promise[Unit]             = js.native
  def connect(): js.Promise[ConnectionPool] = js.native
  val connected: Boolean                    = js.native
  val connecting: Boolean                   = js.native
  val driver: String                        = js.native
}

@js.native
trait RecordSet[A] extends js.Array[A] {
  val columns: js.Any
  // def toTable()
}

@js.native
trait SQLResult[A] extends js.Object {
  val recordsets: js.Array[RecordSet[A]]
  val recordset: RecordSet[A]
  val rowsAffected: js.Array[Int]
  val output: js.Dictionary[js.Any]
}

@js.native
trait Request extends js.Object with IEventEmitter {
  def input(p: String, t: Int, value: js.Any): Request = js.native
  def input(p: String, value: js.Any): Request         = js.native
  var stream: js.UndefOr[Boolean]                      = js.native
  var cancelled: js.UndefOr[Boolean]                   = js.native
  var verbose: js.UndefOr[Boolean]                     = js.native
  def query[A](q: String): js.Promise[SQLResult[A]]    = js.native
  def cancel(): Unit                                   = js.native
  // def execute(procedureName: String): ??? = js.native
}
