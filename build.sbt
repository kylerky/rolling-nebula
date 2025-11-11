val scala3Version = "3.7.3"

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
        "com.typesafe" % "config" % "1.4.3"
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
