package com.nebula.rolling

import cats.effect.{IO, ExitCode}
import cats.implicits._
import fs2.io.file.Path
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger

object CertRollerApp extends CommandIOApp(
  name = "cert-roller",
  header = "Nebula Certificate Roller - Generates CA and signs host certificates."
) {
  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  val baseDirOpt: Opts[Path] = Opts.argument[String]("base-directory")
    .withDefault(System.getProperty("user.dir"))
    .map(Path(_))
    .withHelp("Base directory for operations (e.g., where 'pubs' and 'config_YYYY-MM-DD' will be created).")

  override def main: Opts[IO[ExitCode]] = baseDirOpt.map { baseDir =>
    for {
      config <- ConfigLoader.loadCertRollerConfig()
      pubDir = baseDir / config.pubDir
      outputDir <- FileSystem.createOutputDirectory(baseDir)
      _ <- logger.info(s"Created output directory: $outputDir")
      _ <- NebulaCert.generateCA(config.caName, outputDir)
      _ <- logger.info("Generated CA certificate and key.")
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
            ) *> logger.info(s"Signed certificate for $hostName.")
          case None =>
            logger.warn(s"No configuration found for host '$hostName'. Skipping.")
        }
      }
    } yield ExitCode.Success
  }.handleErrorWith { err =>
    logger.error(err)(s"An error occurred: ${err.getMessage}") *> IO(ExitCode.Error)
  }
}
