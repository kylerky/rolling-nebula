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
        // All these are pure values, not IO effects, so they can be defined outside the for-comprehension
        val fileSystem = new DefaultFileSystem()

        // This part needs to be an IO[ExitCode]
        ConfigLoader.loadConfigServerConfig(cliConfigOpt).flatMap { baseConfig =>
          val finalConfig = cliNumConfigsOpt
            .map(numConfigs => baseConfig.copy(numConfigs = Some(numConfigs)))
            .getOrElse(baseConfig)

          val templateService = new TemplateService(finalConfig, fileSystem)
          val privilegedConfigService = new PrivilegedConfigService(
            finalConfig,
            templateService
          )

          val serverHostStr = cliHostOpt.getOrElse(finalConfig.host)
          val serverPortInt = cliPortOpt.getOrElse(finalConfig.port)

          // Define Tapir routes
          val unifiedTemplateRoute = Http4sServerInterpreter[IO]().toRoutes(
            Endpoints.unifiedTemplateEndpoint.serverLogic {
              case (remoteAddress, allowInboundGroups, limit, templateName) =>
                templateService.getConfig(templateName, remoteAddress, limit, allowInboundGroups)
            }
          )
          val privilegedConfigRoute = Http4sServerInterpreter[IO]().toRoutes(
            Endpoints.privilegedConfigEndpoint.serverLogic {
              case (remoteAddress, allowInboundGroups, limit, ipFromPath) =>
                privilegedConfigService.getConfig(remoteAddress, allowInboundGroups, limit, ipFromPath)
            }
          )

          // Combine routes
          val httpApp =
            (unifiedTemplateRoute <+> privilegedConfigRoute).orNotFound

          // Add http4s logging
          val loggedHttpApp = Http4sLogger.httpApp(
            logHeaders = true,
            logBody = false
          )(httpApp)

          // Now, the IO effects for serverHost and http4sPort
          for {
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

    }
