import com.nebula.rolling.util.BaseApp

object ConfigServerApp
    extends BaseApp(
      name = "config-server",
      header =
        "Nebula Configuration Server - Serves dynamic Nebula configurations."
    ) {
  override protected implicit def logger: Logger[IO] = Slf4jLogger.getLogger[IO]
  implicit val asyncIO: Async[IO] = IO.asyncForIO // Provide Async[IO] explicitly

  val portOpt: Opts[Option[Int]] = Opts
    .option[Int]("port", short = "p", help = "Port to bind the server to.")
    .orNone
  val hostOpt: Opts[Option[String]] = Opts
    .option[String]("host", short = "h", help = "Host to bind the server to.")
    .orNone

  override def app: Opts[IO[ExitCode]] = (portOpt, hostOpt).mapN {
    (cliPortOpt, cliHostOpt) =>
      for {
        config <- ConfigLoader.loadConfigServerConfig()
        serverHostStr = cliHostOpt.getOrElse(config.host)
        serverPortInt = cliPortOpt.getOrElse(config.port)

        serverHost <- Host
          .fromString(serverHostStr)
          .liftTo[IO](
            new IllegalArgumentException(s"Invalid host specified: $serverHostStr")
          )
        http4sPort <- Port
          .fromInt(serverPortInt)
          .liftTo[IO](
            new IllegalArgumentException(
              s"Invalid port number specified: $serverPortInt"
            )
          )

        templateService = new TemplateService(config)

        // Define Tapir routes
        labServerRoute = Http4sServerInterpreter[IO]().toRoutes(
          Endpoints.labServerEndpoint.serverLogic(
            templateService.getLabServerConfig
          )
        )
        defaultRoute = Http4sServerInterpreter[IO]().toRoutes(
          Endpoints.defaultEndpoint.serverLogic(
            templateService.getDefaultConfig
          )
        )

        // Combine routes
        httpApp = (labServerRoute <+> defaultRoute).orNotFound

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
