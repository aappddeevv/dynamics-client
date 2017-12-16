// Copyright (c) 2017 aappddeevv@gmail.com
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package etl

import scala.scalajs.js
import js._
import js.Dynamic.{literal => jsobj}
import JSConverters._
import scala.concurrent._
import fs2._
import cats._
import cats.data._
import cats.implicits._

import dynamics.common._
import dynamics.common.implicits._

/**
  * Helpers when working with scalajs data, specifically, js.Object, js.Dynamic and
  * js.Dictionary. Most of these mutate the input source so create a copy of the data
  * prior to calling if you want.
  */
package object jsdatahelpers {

  /**
    * Assuming a property is a string, omit the property from the object if its blank.
    * This is a mutating action.
    */
  @inline
  def pruneIfBlank(obj: js.Dictionary[js.Any], p: String*): obj.type = {
    p.foreach { k =>
      if (isBlank(obj, k)) omit(obj, k)
    }
    obj
  }

  /** Slow but steady deep copy using JSON.stringify and JSON.parse. */
  @inline
  def deepCopy(obj: js.Object): js.Object = JSON.parse(JSON.stringify(obj)).asInstanceOf[js.Object]

  @inline
  def replaceIfBlank(obj: js.Dictionary[js.Any], p: String, f: String => js.Any): obj.type =
    xfObj[String](obj, p, {
      case a if (a.size == 0) => obj += (p -> f(a))
    })

  /** True if property is present on the object. */
  @inline
  def isDefined(obj: js.Dictionary[_], p: String) = if (obj.contains(p)) true else false

  /** If property is defined, do something to object, otherwise return original object. */
  def doIfDefined(obj: js.Dictionary[js.Any], p: String, f: js.Dictionary[js.Any] => Unit): obj.type = {
    if (isDefined(obj, p)) f(obj)
    obj
  }

  /** If property is present, return true if its value is string blank or null, otherwise, return false. */
  @inline
  def isBlank(obj: js.Dictionary[js.Any], p: String): Boolean =
    obj
      .get(p)
      .map(v => if (v == null) "" else v)
      .map(_.asInstanceOf[String])
      .filterNot(_.isEmpty)
      .map(_ => true)
      .getOrElse(false)

  /** If property is present, replace with value regardless of the current value. */
  @inline
  def setTo(obj: js.Dictionary[js.Any], p: String, value: js.Any): obj.type =
    doIfDefined(obj, p, { dict =>
      dict += (p -> value)
    })

  @inline
  def roundAt(p: Int)(n: Double): Double = { val s = math pow (10, p); (math round n * s) / s }

  /** Get a value in the js object and return it wrapped in `Option`. If found,
    * it is removed from the object, a mutating operation.
    */
  @inline
  def getAndZap[A](j: js.Dictionary[js.Any], p: String): Option[A] = {
    val r = j.get(p).asInstanceOf[Option[A]]
    r.foreach(_ => j -= p)
    r
  }

  /** Removes keys if present. Mutates input object. lodash naming. */
  @inline
  def omit(j: js.Dictionary[_], keys: String*): j.type = {
    keys.foreach { j.remove(_) }
    j
  }

  @inline
  def keepOnly(obj: js.Dictionary[js.Any], names: String*): js.Object = {
    val keys = js.Object.properties(obj)
    updateObject(keys -- names, Nil, obj.asInstanceOf[js.Object])
  }

  /** Rename keys. Mutates input object. Old keys are removed as they
    * are moved. If a key is not found, that property is not renamed.
    */
  @inline
  def rename(j: js.Dictionary[js.Any], renames: (String, String)*): j.type = {
    renames.foreach {
      case (o, n) =>
        j.get(o) match {
          case Some(v) =>
            j -= o
            j(n) = v
          case None => // do nothing
        }
    }
    j
  }

  /** Update an object. Mutates in place! Processng order is drops then renames. */
  def updateObject[A](drops: Seq[String], renames: Seq[(String, String)], obj: js.Object): js.Object = {
    val d = obj.asInstanceOf[js.Dictionary[js.Any]]
    omit(d, drops: _*)
    rename(d, renames: _*)
    obj
  }

  /** If property is present, apply partial function. */
  def xfObj[A](obj: js.Dictionary[js.Any], p: String, pf: PartialFunction[A, Unit]): obj.type =
    doIfDefined(obj, p, dict => {
      val a = dict(p).asInstanceOf[A]
      if (pf.isDefinedAt(a)) pf.apply(a)
    })

  /**
    * Given some string definitions of keep, drop and rename, create a function that
    * that mutates a js.Object.
    */
  def stdConverter(keeps: Option[String], drops: Option[String], renames: Option[String]): js.Object => js.Object = {
    val keep = keeps.map(_.split('|').map(_.trim))
    val drop = drops.map(_.split('|').map(_.trim))
    val rename: Option[Seq[(String, String)]] =
      renames.map(_.split('|').map(_.split("->")).map(arr => (arr(0).trim, arr(1).trim)))

    obj =>
      {
        val o = obj.asDict[js.Any]
        drop.foreach(omit(o, _: _*))
        rename.foreach(jsdatahelpers.rename(o, _: _*))
        keep.foreach(keepOnly(o, _: _*))
        obj
      }
  }

}
