package com.nebula.rolling

import cats.effect.{IO, IOApp, ExitCode}
import cats.implicits._
import fs2.io.file.Path
import java.io.File

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    val baseDir = Path(System.getProperty("user.dir"))
    val pubDir = baseDir / "pub"

    (for {
      config <- ConfigLoader.load()
      outputDir <- FileSystem.createOutputDirectory(baseDir)
      _ <- IO.println(s"Created output directory: $outputDir")
      _ <- NebulaCert.generateCA(config.caName, outputDir)
      _ <- IO.println("Generated CA certificate and key.")
      pubKeyFiles <- FileSystem.getPublicKeyFiles(pubDir).compile.toList
      _ <- pubKeyFiles.traverse_ { pubKeyPath =>
        val hostName = pubKeyPath.fileName.toString.stripSuffix(".pub")
        config.hosts.get(hostName) match {
          case Some(hostConfig) =>
            NebulaCert.signHostKey(
              caCrt = outputDir / "ca.crt",
              caKey = outputDir / "ca.key",
              pubKey = pubKeyPath,
              hostConfig = hostConfig,
              outputDir = outputDir
            ) *> IO.println(s"Signed certificate for $hostName.")
          case None =>
            IO.println(s"Warning: No configuration found for host '$hostName'. Skipping.")
        }
      }
    } yield ExitCode.Success).handleErrorWith { err =>
      IO.println(s"An error occurred: ${err.getMessage}") *> IO(ExitCode.Error)
    }
  }
}