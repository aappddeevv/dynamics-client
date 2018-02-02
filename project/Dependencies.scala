import sbt._
import org.scalajs.sbtplugin._
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

object Dependencies {

  val monocleVersion = "1.5.0-cats"

  /** Dependencies that are jvm/js */
  val commonDependencies = Def.setting(Seq(
    "com.github.scopt"       %%% "scopt"       % "latest.version",
    "org.scalatest"          %%% "scalatest"    % "latest.release" % "test",
    "co.fs2" %%% "fs2-core" % "0.10.0-RC1",
    "org.typelevel" %%% "cats-core" % "1.0.1",
    "org.typelevel" %%% "cats-effect" % "0.8",
    //"com.github.mpilquist" %%% "simulacrum" % "0.10.0",
    "io.monadless" %%% "monadless-core" % "latest.version",
    "io.monadless" %%% "monadless-stdlib" % "latest.version",
    "io.monadless" %%% "monadless-cats" % "latest.version",
    "com.typesafe.play" %%% "play-json" % "2.6.0",
    "com.softwaremill.retry" %%% "retry" % "0.3.0",
    "com.definitelyscala" %%% "scala-js-xmldoc" % "latest.release",
    "com.github.cb372" %%% "scalacache-core" % "0.10.0",
    "org.scala-js" %%% "scalajs-java-time" % "latest.version",
    "org.scala-sbt" % "test-interface" % "1.0",
    "com.github.julien-truffaut" %%%  "monocle-core"  % monocleVersion,
    "com.github.julien-truffaut" %%%  "monocle-macro" % monocleVersion,
  ))

  /** js only libraries */
  val myJSDependencies = Def.setting(Seq(
    "io.scalajs.npm" %%% "chalk" % "latest.version",
    "io.scalajs.npm" %%% "node-fetch" % "latest.version",
    "io.scalajs"             %%% "nodejs"      % "latest.version",
    "io.scalajs.npm" %%% "csv-parse" % "latest.version",
    "io.scalajs.npm" %%% "winston" % "0.4.0",
    "io.scalajs.npm" %%% "xml2js" % "0.4.0",
  ))

  val commonScalacOptions = Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-language:_",
    "-unchecked",
    "-Yno-adapted-args",
    "-Ywarn-numeric-widen",
    "-Xfuture",
    "-Ypartial-unification"
  )
}
