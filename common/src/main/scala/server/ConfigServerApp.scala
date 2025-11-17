package com.teecertlabs.nebula.rolling.server

import cats.effect.{IO, ExitCode}
import cats.implicits._
import com.monovore.decline.Opts
import com.teecertlabs.nebula.rolling.util.BaseApp
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import com.teecertlabs.nebula.rolling.{ConfigLoader, FileSystem}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.Logger as Http4sLogger
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.server.http4s.Http4sServerInterpreter._
import com.comcast.ip4s._
import cats.effect.kernel.Async
import java.io.File
import com.teecertlabs.nebula.rolling.DefaultFileSystem

object ConfigServerApp
    extends BaseApp(
      name = "config-server",
      header =
        "Nebula Configuration Server - Serves dynamic Nebula configurations."
    ) {
  override protected implicit def logger: Logger[IO] = Slf4jLogger.getLogger[IO]
  implicit val asyncIO: Async[IO] =
    IO.asyncForIO // Provide Async[IO] explicitly

  val portOpt: Opts[Option[Int]] = Opts
    .option[Int]("port", short = "p", help = "Port to bind the server to.")
    .orNone
  val hostOpt: Opts[Option[String]] = Opts
    .option[String]("host", short = "h", help = "Host to bind the server to.")
    .orNone
  val configOpt: Opts[Option[File]] = Opts
    .option[String]("config", help = "Path to the configuration file.")
    .map(new File(_))
    .orNone
  val numConfigsOpt: Opts[Option[Int]] = Opts
    .option[Int](
      "num-configs",
      help = "Number of configurations to serve (0 for all)."
    )
    .orNone

  override def app: Opts[IO[ExitCode]] =
    (portOpt, hostOpt, configOpt, numConfigsOpt).mapN {
      (
          cliPortOpt,
          cliHostOpt,
          cliConfigOpt,
          cliNumConfigsOpt
      ) =>
        for {
          baseConfig <- ConfigLoader.loadConfigServerConfig(cliConfigOpt)

          // Establish precedence: CLI > Config File
          finalConfig = cliNumConfigsOpt
            .map(numConfigs => baseConfig.copy(numConfigs = Some(numConfigs)))
            .getOrElse(baseConfig)

          fileSystem = new DefaultFileSystem()
          templateService = new TemplateService(finalConfig, fileSystem)
          privilegedConfigService = new PrivilegedConfigService(
            finalConfig,
            templateService
          )

          serverHostStr = cliHostOpt.getOrElse(finalConfig.host)
          serverPortInt = cliPortOpt.getOrElse(finalConfig.port)

          serverHost <- Host
            .fromString(serverHostStr)
            .liftTo[IO](
              new IllegalArgumentException(
                s"Invalid host specified: $serverHostStr"
              )
            )
          http4sPort <- Port
            .fromInt(serverPortInt)
            .liftTo[IO](
              new IllegalArgumentException(
                s"Invalid port number specified: $serverPortInt"
              )
            )

          // Define Tapir routes
          labServerRoute = Http4sServerInterpreter[IO]().toRoutes(
            Endpoints.labServerEndpoint.serverLogic {
              case (remoteAddress, limit) =>
                templateService.getConfig("lab_server", remoteAddress, limit)
            }
          )
          defaultRoute = Http4sServerInterpreter[IO]().toRoutes(
            Endpoints.defaultEndpoint.serverLogic {
              case (remoteAddress, limit) =>
                templateService.getConfig("default", remoteAddress, limit)
            }
          )
          privilegedConfigRoute = Http4sServerInterpreter[IO]().toRoutes(
            Endpoints.privilegedConfigEndpoint.serverLogic {
              case (ipFromPath, remoteAddress) =>
                privilegedConfigService.getConfig(remoteAddress, ipFromPath)
            }
          )

          // Combine routes
          httpApp =
            (labServerRoute <+> defaultRoute <+> privilegedConfigRoute).orNotFound

          // Add http4s logging
          loggedHttpApp = Http4sLogger.httpApp(
            logHeaders = true,
            logBody = false
          )(httpApp)

          _ <- EmberServerBuilder
            .default[IO]
            .withHost(serverHost)
            .withPort(http4sPort)
            .withHttpApp(loggedHttpApp)
            .build
            .use { server =>
              logger.info(s"Config Server started at ${server.address}") *>
                IO.never // Keep the server running indefinitely
            }
        } yield ExitCode.Success
    }
}
