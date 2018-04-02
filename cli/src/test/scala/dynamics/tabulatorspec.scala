// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics

import org.scalatest._

class TabulatorSpecs extends FlatSpec
    with Matchers with OptionValues {

  /*
  import tabulator._

  "tabulator" should "align text" in {
    alignLeft(10, ' ')("blah") shouldBe "blah      "
  }

  it should "align a string that is too long" in {
    alignLeft(4, ' ')("blah hah") shouldBe "blah hah"
  }

  it should "render a cell with short value" in {
    renderCell(Option("blah"), x => Iterator(x), alignLeft(6, ' ')) shouldBe Seq(Option("blah  "))
  }

  it should "wrap a string that is too long" in {
    val width = 36
    val x = "8af7e89b-f7e3-4775-b427-fe68c67c19b1"
    val result = renderCell(Option(x), verticalChopper(width), alignLeft(width, '.'))
    result shouldBe Seq(Option(x))
  }

  it should "reduce into multiple rows" in {
    val width = 4
    val colvalue = "blah this is great I think"
    val col = renderCell(Option(colvalue), verticalChopper(4), alignLeft(width, ' '))
    val lines = reduce(Seq(col),
      _ => throw new RuntimeException("should not happen"),
      _ => " " * width)
    lines shouldBe Seq("blah", " thi", "s is", " gre", "at I", " thi", "nk  ")
  }

  it should "intersperse" in {
    intersperse(Seq("blah", "hah"), drawLine) shouldBe Seq("blah", "----", "hah")
  }

  "free monad" should "allow creation of a program" in {

    import cats._
    import cats.data._
    import cats.implicits._

    case class Data(i: Int, s: String, d: Double)
    val data = Data(1,"blah", 2.0)

    // our program produces a single row of output as a string
    def program: Table[String] =
      for {
        _ <- addCell("i", Cell.cell(data, (d: Data) => Option(d.i)))
        _ <- addCell("s", Cell.cell(data, (d: Data) => Option(d.s)))
        _ <- addCell("d", Cell.pure(data.d))
        _ <- addCell("derived", Cell.pure("a special note"))
        r <- renderRow
      } yield r

    def compiler: TableA ~> Id = new (TableA ~> Id){

      var values = collection.mutable.HashMap[String, String]()

      def apply[A](fa: TableA[A]): Id[A] =
        fa match {
          case AddCell(column, cell) => cell match {
            case c:Cell[Int] => values(column) = c.value.toString
          }
          case RenderRow() =>
            values = collection.mutable.HashMap[String, String]()
            "rendered row"
        }
    }

    val result = program.foldMap(compiler)
    result shouldBe "rendered row"
  }
   */
}
