// Copyright (c) 2017 aappddeevv@gmail.com
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics
package common

import cats.Monoid
//import cats.free.Free
//import Free.liftF
import cats.arrow.FunctionK
import cats.{Id, ~>}

/*

/**
 *  Partially stolen from https://github.com/maxaf/tabula and other places.
 */
object tabulator {

  trait Cell[+A] { cell =>
    def value: Option[A]

    def map[B](f: A => B): Cell[B] = new Cell[B] {
      lazy val value = cell.value.map(f)
    }

    def flatMap[B](f: A => Option[B]): Cell[B] = new Cell[B] {
      lazy val value = cell.value.flatMap(f)
    }
  }

  /**
 * Cell definition that converts a source of data (the data and an extractor) to a Cell.
 */
  case class LazyCell[F, A](source: F, extractor: F => Option[A]) extends Cell[A] {
    lazy val value = extractor(source)
  }

  object Cell {
    def cell[F, A](source: F, extractor: F => Option[A]) = LazyCell(source, extractor)
    def pure[A](v: Option[A])                            = new Cell[A] { val value = v }
    def pure[A](v: A)                                    = new Cell[A] { val value = Option(v) }
  }

  sealed trait TableA[A]
  case class AddCell[A](column: String, cell: Cell[A]) extends TableA[Unit]
  case class RenderRow()                               extends TableA[String]

  type Table[A] = Free[TableA, A]

  def addCell[A](column: String, cell: Cell[A]): Table[Unit] =
    liftF[TableA, Unit](AddCell[A](column, cell))
  def renderRow: Table[String] = liftF[TableA, String](RenderRow())

  /** A cell in a table that may have multiple physical lines. */
  type MultiCell = Seq[Option[String]]
  val EmptyCell = Seq(None)

  /** A transformer function for a cell. */
  type Fill = (Int, Char) => String => String

  val alignLeft: Fill = (w, f) => v => v.padTo(w, f)

  /**
 *  Vertically chop a string into pieces removing leading
 *  whitespaces on each segment wherever necessary. Do not use
 *  this for tabular printing if you are printing identifiers or
 *  other values where whitespace is significant.
 */
  def verticalChopper(width: Int): String => Iterator[String] = v => {
    // expensive
    //var leftover = v
    v.grouped(width)
  }

  /**
 * Render a string into a formatted string potentially spanning multiple
 * logical output rows. Suitable or fixed char width, mono-font
 * outputs like terminals.
 * @param value An optional string value to render.
 * @param chopper Chop a string into fixed width pieces IF it does not fit on one line.
 *  You can put "..." nicities or vertical alignment here if desired.
 * @param tf Transform chopped values. Use a curried alignLeft to left align.
 * @return A list of cells. Length > 1 means cell spans multiple lines.
 */
  def renderCell(value: Option[String], chopper: String => Iterator[String], tf: String => String): MultiCell = {
    value match {
      case Some(s) =>
        chopper(s).toSeq
          .map(tf)
          .map(Option(_))
      case None => Seq(None)
    }
  }

  /** Hack to reduce multiple logical "rows" within a column to multiple output rows.
 * Each column position may not have the same number of logical rows except
 * the first logical row, all of which must have a Some() or None.
 * @param row Row of data.
 * @param spacer Image to put after a column i.e. between columns.
 * @param missing Image for column if column's value is missing.
 * @return A set of physical output rows that are a logical row.
 */
  def reduce(row: Seq[MultiCell], spacer: Int => String, missing: Int => String): Seq[String] = {
    val len              = row.length
    def dospacer(c: Int) = if (c < len - 1) spacer(c) else ""
    val slice = row
      .map(_(0))
      .zipWithIndex
      .map { case (vopt, i) => (vopt orElse Option(missing(i))).map(_ + dospacer(i)).get }
      .mkString

    // inefficient for generating the next image line
    val nextrow = row.map { v =>
      if (v.length > 1) v.drop(1) // take the cell image for the next logical line
      else EmptyCell
    }
    val hasNextRow = nextrow.filter(_ == EmptyCell).length != nextrow.length
    Seq(slice) ++ (if (hasNextRow) reduce(nextrow, spacer, missing) else Nil)
  }

  val drawLine: (Int, Int) => Option[String] = (i, w) => Option("-" * w)

  /**
 * Intersperse between lines. No separator is called after the last line.
 * We should do this lazily....
 * @param sep Line separate. Input is the "above line index" and width.
 */
  def intersperse(lines: Seq[String], sep: (Int, Int) => Option[String]): Seq[String] = {
    val out = collection.mutable.ListBuffer[String]()
    val len = lines.length
    for (x <- lines.zipWithIndex) {
      val i = x._2
      out += x._1
      if (i < len - 1) sep(i, x._1.length).foreach(out += _)
    }
    out.toSeq
  }

  def format(table: Seq[Seq[Any]]) = table match {
    case Seq() => ""
    case _ =>
      val sizes    = for (row <- table) yield (for (cell <- row) yield if (cell == null) 0 else cell.toString.length)
      val colSizes = for (col <- sizes.transpose) yield col.max
      val rows     = for (row <- table) yield formatRow(row, colSizes)
      formatRows(rowSeparator(colSizes), rows)
  }

  def formatRows(rowSeparator: String, rows: Seq[String]): String =
    (rowSeparator ::
      rows.head ::
      rowSeparator ::
      rows.tail.toList :::
      rowSeparator ::
      List()).mkString("\n")

  def formatRow(row: Seq[Any], colSizes: Seq[Int]) = {
    val cells = (for ((item, size) <- row.zip(colSizes)) yield if (size == 0) "" else ("%" + size + "s").format(item))
    cells.mkString("|", "|", "|")
  }

  def rowSeparator(colSizes: Seq[Int]) = colSizes map { "-" * _ } mkString ("+", "+", "+")
}
 */
