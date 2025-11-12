package com.nebula.rolling.server

import cats.effect.{IO, ExitCode}
import cats.implicits._
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import com.nebula.rolling.ConfigLoader
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.Logger as Http4sLogger
import com.softwaremill.sttp.tapir.server.http4s.Http4sServerInterpreter

object ConfigServerApp extends CommandIOApp(
  name = "config-server",
  header = "Nebula Configuration Server - Serves dynamic Nebula configurations."
) {
  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  val portOpt: Opts[Option[Int]] = Opts.option[Int]("port", short = "p", help = "Port to bind the server to.").orNone
  val hostOpt: Opts[Option[String]] = Opts.option[String]("host", short = "h", help = "Host to bind the server to.").orNone

  override def main: Opts[IO[ExitCode]] = (portOpt, hostOpt).mapN { (cliPortOpt, cliHostOpt) =>
    for {
      config <- ConfigLoader.loadConfigServerConfig()
      serverHost = cliHostOpt.getOrElse(config.host)
      serverPort = cliPortOpt.getOrElse(config.port)

      http4sPort <- IO.fromOption(org.http4s.Uri.Port.fromInt(serverPort))(
        new IllegalArgumentException(s"Invalid port number specified: $serverPort")
      )

      templateService = new TemplateService(config.templatePath)

      // Define Tapir routes
      labServerRoute = Http4sServerInterpreter[IO]().toHttpRoutes(Endpoints.labServerEndpoint.serverLogic(templateService.getLabServerConfig))
      defaultRoute = Http4sServerInterpreter[IO]().toHttpRoutes(Endpoints.defaultEndpoint.serverLogic(templateService.getDefaultConfig))

      // Combine routes
      httpApp = (labServerRoute <+> defaultRoute).orNotFound

      // Add http4s logging
      loggedHttpApp = Http4sLogger.httpApp(logHeaders = true, logBody = false)(httpApp)

      _ <- EmberServerBuilder.default[IO]
        .withHost(serverHost)
        .withPort(http4sPort)
        .withHttpApp(loggedHttpApp)
        .build
        .use { server =>
          logger.info(s"Config Server started at ${server.address}") *>
          IO.never // Keep the server running indefinitely
        }
    } yield ExitCode.Success
  }.handleErrorWith { err =>
    logger.error(err)(s"An error occurred: ${err.getMessage}") *> IO(ExitCode.Error)
  }
}
