package com.teecertlabs.nebula.rolling

import cats.effect.{IO, ExitCode}
import cats.implicits._
import fs2.io.file.Path
import com.monovore.decline.Opts
import com.teecertlabs.nebula.rolling.util.BaseApp
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object CertRollerApp
    extends BaseApp(
      name = "cert-roller",
      header =
        "Nebula Certificate Roller - Generates CA and signs host certificates."
    ) {
  override protected implicit def logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  val baseDirOpt: Opts[Path] = Opts
    .argument[String](metavar = "base-directory")
    .withDefault(System.getProperty("user.dir"))
    .map(Path(_))

  override def app: Opts[IO[ExitCode]] = baseDirOpt.map { baseDir =>
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
            logger.warn(
              s"No configuration found for host '$hostName'. Skipping."
            )
        }
      }
    } yield ExitCode.Success
  }
}
