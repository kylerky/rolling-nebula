package com.teecertlabs.nebula.rolling

import cats.effect.{IO, ExitCode}
import cats.implicits._
import fs2.io.file.{Files, Path}
import fs2.Stream
import com.monovore.decline.Opts
import com.teecertlabs.nebula.rolling.util.BaseApp
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import java.io.File

object CertRollerApp
    extends BaseApp(
      name = "cert-roller",
      header =
        "Nebula Certificate Roller - Generates CA and signs host certificates."
    ) {
  override protected given logger: Logger[IO] = Slf4jLogger.getLogger[IO]
  given FileSystem = new DefaultFileSystem

  val baseDirOpt: Opts[Path] = Opts
    .argument[String](metavar = "base-directory")
    .withDefault(System.getProperty("user.dir"))
    .map(Path(_))

  val configOpt: Opts[Option[File]] = Opts
    .option[String]("config", help = "Path to the configuration file.")
    .map(new File(_))
    .orNone

  private val tempDirName = "tmp_config"

  override def app: Opts[IO[ExitCode]] = {
    val program = new CertRollerProgram(NebulaCertService.live)

    val rollCmd = Opts.subcommand(
      name = "roll",
      help = "Batch generates certificates for all nodes."
    ) {
      (baseDirOpt, configOpt).mapN { (baseDir, cliConfigOpt) =>
        val certGeneration = for {
          config <- ConfigLoader.loadCertRollerConfig(cliConfigOpt)
          _ <- program.roll(baseDir, config)
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

    val updateCmd = Opts.subcommand(
      name = "update",
      help = "Generates/refreshes the certificate for a single specific host."
    ) {
      val hostnameOpt = Opts.argument[String]("hostname")
      val pubKeyOpt =
        Opts.option[String]("pub-key", "Path to the new public key file.").orNone

      (baseDirOpt, configOpt, hostnameOpt, pubKeyOpt).mapN {
        (baseDir, cliConfigOpt, hostname, cliPubKeyPath) =>
          val updateLogic = for {
            config <- ConfigLoader.loadCertRollerConfig(cliConfigOpt)
            _ <- program.update(baseDir, config, hostname, cliPubKeyPath.map(Path(_)))
          } yield ()

          updateLogic.attempt.flatMap {
            case Left(err) =>
              logger.error(err)("Update failed.") *> IO.pure(ExitCode.Error)
            case Right(_) =>
              IO.pure(ExitCode.Success)
          }
      }
    }

    rollCmd.orElse(updateCmd)
  }
}
