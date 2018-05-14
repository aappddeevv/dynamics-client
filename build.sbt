import scala.sys.process._

resolvers += Resolver.sonatypeRepo("releases")
//resolvers += Resolver.sonatypeRepo("snapshots")
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
resolvers += Resolver.typesafeRepo("snapshots")
resolvers += Resolver.bintrayRepo("softprops", "maven") // for retry, what else?
resolvers += Resolver.bintrayRepo("scalameta", "maven") // for latset scalafmt
resolvers += Resolver.jcenterRepo

// placeholder for scala-js 1.x, must be placed in settings
//scalaJSLinkerConfig ~= {_.withModuleKind(ModuleKind.CommonJSModule) }

autoCompilerPlugins := true

lazy val licenseSettings = Seq(
  headerMappings := headerMappings.value +
    (HeaderFileType.scala -> HeaderCommentStyle.cppStyleLineComment),
    headerLicense  := Some(HeaderLicense.Custom(
      """|Copyright (c) 2017 The Trapelo Group LLC
         |This software is licensed under the MIT License (MIT).
         |For more information see LICENSE or https://opensource.org/licenses/MIT
         |""".stripMargin
    )))

lazy val buildSettings = Seq(
  organization := "com.github.aappddeevv.dynamics",
  licenses ++= Seq(("MIT", url("http://opensource.org/licenses/MIT"))),

  scalaVersion := "2.12.4",
  scalaModuleInfo ~= (_.map(_.withOverrideScalaVersion(true))),
  scalafmtVersion in ThisBuild := "1.5.1",    
) ++ licenseSettings

lazy val noPublishSettings = Seq(
  skip in publish := true,
  publish := {},
  publishLocal := {},
  publishArtifact := false
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
  addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.4"),
  addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
)

lazy val dynamicsSettings = buildSettings ++ commonSettings

lazy val root = project.in(file("."))
  .settings(dynamicsSettings)
  .settings(noPublishSettings)
  .settings(name := "dynamics-client")
  .aggregate(http, client, clientcommon, common, etl, cli, `cli-main`, docs, adal, apps)
  .enablePlugins(ScalaJSPlugin, AutomateHeaderPlugin)

lazy val common = project
  .settings(dynamicsSettings)
  .settings(description := "Common components")
  .settings(libraryDependencies ++= Dependencies.monadlessDependencies.value)
  .settings(name := "dynamics-client-common")
  .enablePlugins(ScalaJSPlugin, AutomateHeaderPlugin)

lazy val commonio = project
  .settings(dynamicsSettings)
  .settings(description := "Common components that involve IO (server based).")
  .settings(libraryDependencies ++= Dependencies.monadlessDependencies.value)
  .settings(name := "dynamics-client-common-io")
  .enablePlugins(ScalaJSPlugin, AutomateHeaderPlugin)
  .dependsOn(common)

lazy val apps = project
  .settings(dynamicsSettings)
  .settings(description := "CLI application frameworks")
  .settings(libraryDependencies ++= Dependencies.monadlessDependencies.value)
  .settings(libraryDependencies ++= Dependencies.appDependencies.value)
  .settings(name := "dynamics-client-apps")
  .enablePlugins(ScalaJSPlugin, AutomateHeaderPlugin)
  .dependsOn(cli,client,common,http,commonio)

lazy val clientcommon = project
  .settings(dynamicsSettings)
  .settings(description := "Common client components")
  .settings(libraryDependencies ++= Dependencies.monadlessDependencies.value)
  .settings(name := "dynamics-client-client-common")
  .enablePlugins(ScalaJSPlugin, AutomateHeaderPlugin)
  .dependsOn(common, http)

lazy val etl = project
  .settings(dynamicsSettings)
  .settings(name := "dynamics-client-etl")
  .settings(description := "ETL support")
  .enablePlugins(ScalaJSPlugin, AutomateHeaderPlugin)
  .dependsOn(common, commonio, client, http)

lazy val http = project
  .settings(dynamicsSettings)
  .settings(name := "dynamics-client-http")
  .settings(description := "http client ")
  .enablePlugins(ScalaJSPlugin, AutomateHeaderPlugin)
  .dependsOn(common)

lazy val adal = project
  .settings(dynamicsSettings)
  .settings(name := "dynamics-client-adal")
  .settings(description := "dynamics active directory authentication")
  .enablePlugins(ScalaJSPlugin, AutomateHeaderPlugin)
  .dependsOn(client, common)

lazy val client = project
  .settings(dynamicsSettings)
  .settings(name := "dynamics-client-clients")
  .settings(description := "dynamics client")
  // need to get rid of dependency on commoni
  .dependsOn(http, clientcommon, common, commonio)
  .enablePlugins(ScalaJSPlugin, AutomateHeaderPlugin)

lazy val cli = project
  .settings(dynamicsSettings)
  .settings(name := "dynamics-client-cli")
  .settings(description := "common CLI client infrastructure")
  .settings(libraryDependencies ++=
    Dependencies.cliDependencies.value ++ Dependencies.monadlessDependencies.value)
  .dependsOn(client, etl, adal, commonio)
  .enablePlugins(ScalaJSPlugin, AutomateHeaderPlugin, BuildInfoPlugin)
  .settings(
    unmanagedResourceDirectories in Compile += baseDirectory.value / "cli/src/main/js",
    buildInfoPackage := "dynamics.cli"
  )

lazy val `cli-main` = project
  .settings(dynamicsSettings)
  .settings(name := "dynamics-client-cli-main")
  .settings(description := "CLI main")
  .settings(libraryDependencies ++=
    Dependencies.cliDependencies.value ++ Dependencies.monadlessDependencies.value)
  .settings(
    scalaJSLinkerConfig ~= {_.withModuleKind(ModuleKind.CommonJSModule) },
    scalaJSUseMainModuleInitializer := true,
    mainClass in Compile := Some("dynamics.cli.Main"),
  )
  .dependsOn(cli, commonio) 
  .enablePlugins(ScalaJSPlugin, AutomateHeaderPlugin)

lazy val docs = project
  .settings(buildSettings)
  .settings(noPublishSettings)
  .settings(libraryDependencies ++=
    Dependencies.cliDependencies.value ++ Dependencies.monadlessDependencies.value)
  .enablePlugins(MicrositesPlugin, ScalaUnidocPlugin)
  .dependsOn(clientcommon, client, http, cli, `cli-main`, etl, common, apps).
  settings(
    micrositeName := "dynamics-client",
    micrositeDescription := "A Microsoft Dynamics CLI swiss-army knife and browser/server library.",
    micrositeBaseUrl := "/dynamics-client",
    micrositeGitterChannel := false,
    micrositeDocumentationUrl := "/dynamics-client/docs",
    micrositeAuthor := "aappddeevv",
    micrositeGithubRepo := "dynamics-client",
    micrositeGithubOwner := sys.env.get("GITHUB_USER").getOrElse("unknown"),
    micrositeGithubToken := sys.env.get("GITHUB_TOKEN"),
    micrositePushSiteWith := GitHub4s
  )
  .settings(
    siteSubdirName in ScalaUnidoc := "api",
    addMappingsToSiteDir(mappings in (ScalaUnidoc, packageDoc), siteSubdirName in ScalaUnidoc)
  )

val npmBuild = taskKey[Unit]("fullOptJS then webpack")
npmBuild := {
  (fullOptJS in (`cli-main`, Compile)).value
  "npm run afterScalaJSFull" !
}

val npmBuildFast = taskKey[Unit]("fastOptJS then webpack")
npmBuildFast := {
  (fastOptJS in (`cli-main`, Compile)).value
  "npm run afterScalaJSFast" !
}

addCommandAlias("watchit", "~ ;fastOptJS; npmBuildFast")
addCommandAlias("fmt", ";scalafmt")

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)
// buildInfoPackage := "dynamics-client"

bintrayReleaseOnPublish in ThisBuild := false
bintrayPackageLabels := Seq("scalajs", "dynamics", "dynamics 365", "crm", "microsoft")
bintrayVcsUrl := Some("git:git@github.com:aappddeevv/dynamics")
bintrayRepository := "maven"

