import com.typesafe.sbt.packager.archetypes.JavaAppPackaging

val scala3Version = "3.7.3"

// Common settings for all projects
lazy val commonSettings = Seq(
  version := "0.1.0-SNAPSHOT",
  scalaVersion := scala3Version
)

// All dependencies
lazy val dependencies = Def.setting {
  object V {
    val http4s = "0.23.25"
    val tapir = "1.9.10"
    val decline = "2.4.1"
    val log4cats = "2.6.0"
    val circe = "0.14.6"
    val circeYaml = "0.15.1"
    val catsEffect = "3.5.4"
    val fs2 = "3.10.2"
    val slf4j = "2.0.9"
  }

  val ammoniteVersion = scalaBinaryVersion.value match {
    case "2.10" => "1.0.3"
    case "2.11" => "1.6.7"
    case _      => "3.0.4"
  }
  Seq(
    "io.circe" %% "circe-config" % "0.10.0",
    "org.typelevel" %% "cats-effect" % V.catsEffect,
    "co.fs2" %% "fs2-core" % V.fs2,
    "co.fs2" %% "fs2-io" % V.fs2,
    "io.circe" %% "circe-generic" % V.circe,
    "org.http4s" %% "http4s-ember-server" % V.http4s,
    "org.http4s" %% "http4s-dsl" % V.http4s,
    "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % V.tapir,
    "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % V.tapir,
    "com.monovore" %% "decline-effect" % V.decline,
    "org.typelevel" %% "log4cats-slf4j" % V.log4cats,
    "org.slf4j" % "slf4j-simple" % V.slf4j,
    "io.circe" %% "circe-yaml" % V.circeYaml,
    "org.typelevel" %% "munit-cats-effect-3" % "1.0.7"
  )
}

lazy val root = (project in file("."))
  .aggregate(certRoller, configServer)
  .settings(
    name := "nebula-rolling-root",
    commonSettings
  )

lazy val common = (project in file("common"))
  .settings(
    name := "common",
    commonSettings,
    Test / fork := true,
    libraryDependencies ++= dependencies.value
  )

lazy val certRoller = (project in file("cert-roller"))
  .dependsOn(common)
  .enablePlugins(JavaAppPackaging)
  .settings(
    name := "nebula-cert-roller",
    commonSettings,
    Compile / mainClass := Some("com.teecertlabs.nebula.rolling.CertRollerApp"),
    executableScriptName := "nebula-cert-roller"
  )

lazy val configServer = (project in file("config-server"))
  .dependsOn(common)
  .enablePlugins(JavaAppPackaging)
  .settings(
    name := "nebula-config-server",
    commonSettings,
    Compile / mainClass := Some("com.teecertlabs.nebula.rolling.server.ConfigServerApp"),
    executableScriptName := "nebula-config-server"
  )

ThisBuild / scalafmtOnCompile := true
