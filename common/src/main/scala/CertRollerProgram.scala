package com.teecertlabs.nebula.rolling

import cats.effect.IO
import cats.implicits._
import fs2.io.file.Path
import fs2.Stream
import org.typelevel.log4cats.Logger

class CertRollerProgram(certService: NebulaCertService[IO])(using
    logger: Logger[IO],
    fileSystem: FileSystem
) {

  private val tempDirName = "tmp_config"

  def roll(baseDir: Path, config: CertRollerConfig): IO[Unit] = {
    for {
      pubDir <- IO(Path(config.pubDir))
      tempDir <- fileSystem.createTempDir(baseDir, tempDirName)
      _ <- logger.info(s"Created temporary directory: $tempDir")
      _ <- certService.generateCA(config.caName, tempDir)
      _ <- logger.info("Generated CA certificate and key.")
      pubKeyFiles = fileSystem.getPublicKeyFiles(pubDir)
      _ <- pubKeyFiles
        .evalMap { pubKeyPath =>
          val hostName = pubKeyPath.fileName.toString.stripSuffix(".pub")
          config.hosts.get(hostName) match {
            case Some(hostConfig) =>
              certService.signHostKey(
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
        .compile
        .drain
      timestamp <- fileSystem.getTimestamp
      finalOutputDir = FileSystem.getOutputDirPath(baseDir, timestamp)
      _ <- fileSystem.renameDir(tempDir, finalOutputDir)
      _ <- logger.info(
        s"Successfully created and renamed output to $finalOutputDir"
      )
    } yield ()
  }

  def update(
      baseDir: Path,
      config: CertRollerConfig,
      hostname: String,
      pubKeyPathOpt: Option[Path]
  ): IO[Unit] = {
    val numConfigs = config.numConfigs.getOrElse(11)
    for {
      latestDirs <- fileSystem.findLatestConfigDirs(baseDir, numConfigs)
      _ <-
        if (latestDirs.isEmpty)
          logger.warn("No configuration directories found to update.")
        else IO.unit

      hostConfig <- IO.fromOption(config.hosts.get(hostname))(
        new RuntimeException(s"Host '$hostname' not found in configuration.")
      )

      pubKeyPath <- pubKeyPathOpt match {
        case Some(p) => IO.pure(p)
        case None =>
          val p = Path(config.pubDir) / s"$hostname.pub"
          fileSystem.exists(p).flatMap { exists =>
            if (exists) IO.pure(p)
            else
              IO.raiseError(
                new RuntimeException(
                  s"Public key not found at $p and not provided via --pub-key"
                )
              )
          }
      }

      _ <- Stream
        .emits(latestDirs)
        .evalMap { dir =>
          val caCrt = dir / "ca.crt"
          val caKey = dir / "ca.key"

          (fileSystem.exists(caCrt), fileSystem.exists(caKey)).tupled
            .flatMap {
              case (true, true) =>
                certService.signHostKey(
                  caCrt = caCrt,
                  caKey = caKey,
                  pubKey = pubKeyPath,
                  hostConfig = hostConfig,
                  outputDir = dir
                ) *> logger.info(
                  s"Updated certificate for $hostname in $dir"
                )
              case _ =>
                logger.warn(s"CA files missing in $dir. Skipping.")
            }
        }
        .compile
        .drain
    } yield ()
  }
}