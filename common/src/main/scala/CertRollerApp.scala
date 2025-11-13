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

  private val tempDirName = "tmp_config"

  override def app: Opts[IO[ExitCode]] = baseDirOpt.map { baseDir =>
    val certGeneration = for {
      config <- ConfigLoader.loadCertRollerConfig()
      pubDir = baseDir / config.pubDir
      tempDir <- FileSystem.createTempDir(baseDir, tempDirName)
      _ <- logger.info(s"Created temporary directory: $tempDir")
      _ <- NebulaCert.generateCA(config.caName, tempDir)
      _ <- logger.info("Generated CA certificate and key.")
      pubKeyFiles <- FileSystem.getPublicKeyFiles(pubDir).compile.toList
      _ <- pubKeyFiles.traverse_ { pubKeyPath =>
        val hostName = pubKeyPath.fileName.toString.stripSuffix(".pub")
        config.hosts.get(hostName) match {
          case Some(hostConfig) =>
            NebulaCert.signHostKey(
              caCrt = tempDir / "ca.crt",
              caKey = tempDir / "ca.key",
              pubKey = pubKeyPath,
              hostConfig = hostConfig,
              outputDir = tempDir
            ) *> logger.info(s"Signed certificate for $hostName.")
          case None =>
            logger.warn(
              s"No configuration found for host '$hostName'. Skipping."
            )
        }
      }
      timestamp <- FileSystem.getTimestamp
      finalOutputDir = FileSystem.getOutputDirPath(baseDir, timestamp)
      _ <- FileSystem.renameDir(tempDir, finalOutputDir)
      _ <- logger.info(
        s"Successfully created and renamed output to $finalOutputDir"
      )
    } yield ()

    certGeneration.attempt.flatMap {
      case Left(err) =>
        logger.error(err)(
          s"Certificate generation failed. Temporary directory $tempDirName may still exist for inspection."
        ) *>
          IO.pure(ExitCode.Error)
      case Right(_) =>
        IO.pure(ExitCode.Success)
    }
  }
}
