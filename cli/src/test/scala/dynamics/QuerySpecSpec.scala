package dynamics

import org.scalatest._

class QuerySpecSpec extends FlatSpec with Matchers with OptionValues {

  import dynamics.syntax.queryspec._

  "QuerySpec" should "handle selects" in {
    val q = QuerySpec(filter = Some("hah ne fah"), select = Seq("attr1", "attr2"))
    q.url("blah") shouldBe ("/blah?$select=attr1,attr2&$filter=hah ne fah")
  }

  it should "create a simple url" in {
    val q = QuerySpec()
    q.url("blah") shouldBe ("/blah")
  }

  it should "add property paths" in {
    val e = QuerySpec(properties = Seq(NavProperty("attribute", Some("id"))))
    e.url("entity", Some("id")) shouldBe ("/entity(id)/attribute(id)")
  }

  it should "add a cast" in {
    val e = QuerySpec(cast = Some("asthis"))
    e.url("entity") shouldBe ("/entity/asthis")
  }

  it should "add a property and a select" in {
    val q =
      QuerySpec(select = Seq("takeme"), cast = Some("asthis"), properties = Seq(NavProperty("attribute", Some("id"))))
    q.url("entity", Some("id")) shouldBe ("/entity(id)/attribute(id)/asthis?$select=takeme")
  }

  "Expand" should "expand one $expand" in {
    val e = Expand.render(Seq(Expand("blah")))
    e.value shouldBe ("$expand=blah")
  }

  it should "create 2 expands separated by comma" in {
    val e = Expand.render(
      Seq(
        Expand("blah"),
        Expand("hah")
      ))
    e.value shouldBe ("$expand=blah,hah")
  }

  it should "create 2 expands, commas and selects" in {
    val e = Expand.render(
      Seq(
        Expand("blah", select = Seq("foo")),
        Expand("hah")
      ))
    e.value shouldBe ("$expand=blah($select=foo),hah")
  }

}
