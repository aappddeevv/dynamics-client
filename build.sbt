import scala.sys.process._

resolvers += Resolver.sonatypeRepo("releases")
resolvers += Resolver.bintrayRepo("softprops", "maven") // for retry
resolvers += Resolver.bintrayRepo("scalameta", "maven") // for latset scalafmt
resolvers += Resolver.jcenterRepo


// placeholder for scala-js 1.x
scalaJSLinkerConfig ~= {
  _.withModuleKind(ModuleKind.CommonJSModule)
}

//scalafmtVersion in ThisBuild := "1.3.0" // all projects

lazy val licenseSettings = Seq(
  headerMappings := headerMappings.value +
    (HeaderFileType.scala -> HeaderCommentStyle.cppStyleLineComment),
    headerLicense  := Some(HeaderLicense.Custom(
      """|Copyright (c) 2017 aappddeevv@gmail.com
         |This software is licensed under the MIT License (MIT).
         |For more information see LICENSE or https://opensource.org/licenses/MIT
         |""".stripMargin
    )))

lazy val buildSettings = Seq(
  organization := "com.github.aappddeevv.dynamics",
  licenses ++= Seq(("MIT", url("http://opensource.org/licenses/MIT"))),
  scalaVersion := "2.12.4",
) ++ licenseSettings

lazy val noPublishSettings = Seq(
  skip in publish := true
)

lazy val publishSettings = Seq(
  homepage := Some(url("https://github.com/aappddeevv/dynamics-client"))
)

lazy val commonSettings = Seq(
  scalacOptions ++=
    Dependencies.commonScalacOptions ++
    (if (scalaJSVersion.startsWith("0.6."))
      Seq("-P:scalajs:sjsDefinedByDefault")
    else Nil),
  libraryDependencies ++=
    (Dependencies.commonDependencies.value ++
      Dependencies.myJSDependencies.value),
  addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.4")
)

lazy val dynamicsSettings = buildSettings ++ commonSettings

lazy val root = project.in(file("."))
  .settings(dynamicsSettings)
  .settings(noPublishSettings)
  .settings(name := "dynamics-client")
  .aggregate(http, client, common, etl, cli,
    `cli-main`, docs)
  .enablePlugins(ScalaJSPlugin, AutomateHeaderPlugin)

lazy val common = project
  .settings(dynamicsSettings)
  .enablePlugins(ScalaJSPlugin, AutomateHeaderPlugin)

lazy val etl = project
  .settings(dynamicsSettings)
  .enablePlugins(ScalaJSPlugin, AutomateHeaderPlugin)
  .dependsOn(common)

lazy val http = project
  .settings(dynamicsSettings)
  .enablePlugins(ScalaJSPlugin, AutomateHeaderPlugin)
  .dependsOn(common)

lazy val client = project
  .settings(dynamicsSettings)
  .dependsOn(http, common)
  .enablePlugins(ScalaJSPlugin, AutomateHeaderPlugin)

mainClass in Compile := Some("dynamics.cli.Main")

lazy val cli = project
  .settings(dynamicsSettings)
  .dependsOn(client, common, etl)
  .enablePlugins(ScalaJSPlugin, AutomateHeaderPlugin)

lazy val `cli-main` = project
  .settings(dynamicsSettings)
  .settings(
    scalaJSUseMainModuleInitializer := true,
    mainClass in Compile := Some("dynamics.cli.Main"),
    scalaJSModuleKind := ModuleKind.CommonJSModule
  )
  .dependsOn(client, common, etl, cli) //,searchone
  .enablePlugins(ScalaJSPlugin, AutomateHeaderPlugin)

lazy val docs = project
  .settings(buildSettings)
  .settings(noPublishSettings)
  .enablePlugins(MicrositesPlugin)
  .dependsOn(client, http, cli, `cli-main`, etl, common).
  settings(
    micrositeName := "dynamics-client",
    micrositeDescription := "A Microsoft Dynamics CLI swiss-army knife and browser/server library.",
    micrositeBaseUrl := "/dynamics-client",
    micrositeGitterChannel := false,
    micrositeDocumentationUrl := "/dynamics-client/docs",
    micrositeAuthor := "aappddeevv",
    micrositeGithubRepo := "dynamics-client",
    micrositeGithubOwner :="aappddeevv",
    micrositeGithubToken := Option(scala.sys.env("GITHUB_TOKEN")) orElse None,
    micrositePushSiteWith := GitHub4s
  )

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)

val npmBuild = taskKey[Unit]("fullOptJS then webpack")
npmBuild := {
  val x = (fullOptJS in Compile).value
  "npm run afterScalaJSFull" !
}

val npmBuildFast = taskKey[Unit]("fastOptJS then webpack")
npmBuildFast := {
  //val x = (fastOptJS in Compile).value
  (fastOptJS in Compile).value
  "npm run afterScalaJSFast" !
}

//addCommandAlias("watchit", "~ ;fastOptJS; postScalaJS")

 buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)
// buildInfoPackage := "dynamics-client"

// bintrayPackageLabels := Seq("scalajs", "dynamics", "dynamics 365", "crm", "microsoft")
// bintrayVcsUrl := Some("git:git@github.com:aappddeevv/dynamics")
// bintrayRepository := "maven"
// bintrayPackage := "dynamics"

