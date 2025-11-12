val scala3Version = "3.7.3"

val http4sVersion = "0.23.25"
val tapirVersion = "1.9.10"
val declineVersion = "2.4.1"
val log4catsVersion = "2.6.0"
val circeVersion = "0.14.6" // For circe-generic
val circeYamlVersion = "0.15.1"
val catsEffectVersion = "3.5.4"
val fs2Version = "3.10.2"
val slf4jVersion = "2.0.9"

lazy val root = project
  .in(file("."))
  .settings(
    name := "nebula-rolling",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++= {
      val version = scalaBinaryVersion.value match {
        case "2.10" => "1.0.3"
        case "2.11" => "1.6.7"
        case _ ⇒ "3.0.4"
      }
      Seq(
        "com.lihaoyi" % "ammonite" % version % "test" cross CrossVersion.full,
        "org.scalameta" %% "munit" % "1.0.0" % Test,
        "io.circe" %% "circe-config" % "0.10.0", // circe-config has its own versioning
        "org.typelevel" %% "cats-effect" % catsEffectVersion,
        "co.fs2" %% "fs2-core" % fs2Version,
        "co.fs2" %% "fs2-io" % fs2Version,
        "io.circe" %% "circe-generic" % circeVersion,
        "org.http4s" %% "http4s-ember-server" % http4sVersion,
        "org.http4s" %% "http4s-dsl" % http4sVersion,
        "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % tapirVersion,
        "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % tapirVersion,
        "com.monovore" %% "decline-effect" % declineVersion,
        "org.typelevel" %% "log4cats-slf4j" % log4catsVersion,
        "org.slf4j" % "slf4j-simple" % slf4jVersion,
        "io.circe" %% "circe-yaml" % circeYamlVersion
      )
    },
    sourceGenerators in Test += Def.task {
      val file = (sourceManaged in Test).value / "amm.scala"
      IO.write(
        file,
        """object amm extends App { ammonite.AmmoniteMain.main(args) }"""
      )
      Seq(file)
    }.taskValue,

    (fullClasspath in Test) ++= {
      (updateClassifiers in Test).value
        .configurations
        .find(_.configuration.name == Test.name)
        .get
        .modules
        .flatMap(_.artifacts)
        .collect{case (a, f) if a.classifier == Some("sources") => f}
    }
  )
