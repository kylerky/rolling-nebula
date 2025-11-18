package com.teecertlabs.nebula.rolling.server

import munit.CatsEffectSuite
import cats.effect.IO
import com.teecertlabs.nebula.rolling.ConfigServerConfig
import com.teecertlabs.nebula.rolling.FileSystem
import org.http4s._
import org.http4s.implicits._
import sttp.tapir.server.http4s.Http4sServerInterpreter
import com.comcast.ip4s._

class ServerSuite extends CatsEffectSuite {

  val config = ConfigServerConfig(
    host = "localhost",
    port = 8080,
    templatesDir = "",
    pkiKeyPath = "",
    configDir = "configs",
    privilegedIps = List("192.168.1.100"),
    numConfigs = None
  )

  test("Privileged endpoint should return 200 OK for privileged IP") {
    val fileSystem = new FileSystem {
      override def read(path: String): IO[String] = IO.pure("config content")
      override def list(
          path: fs2.io.file.Path
      ): fs2.Stream[IO, fs2.io.file.Path] = fs2.Stream.empty
      override def exists(path: fs2.io.file.Path): IO[Boolean] = IO.pure(true)
      override def validatePath(
          path: fs2.io.file.Path,
          basePath: fs2.io.file.Path
      ): IO[Unit] = IO.unit
    }
    // Create a mock TemplateService that uses the mock FileSystem
    val mockTemplateService = new TemplateService(config, fileSystem) {
      override def getConfig(
          templateName: String,
          clientIp: Option[java.net.InetSocketAddress],
          limit: Option[Int],
          hostCertIndex: Option[Int],
          allowInboundGroups: Option[List[String]]
      ): IO[Either[HttpError, String]] = {
        assertEquals(templateName, "default")
        IO.pure(Right("config content")) // Return the expected content
      }
    }
    val privilegedConfigService =
      new PrivilegedConfigService(config, mockTemplateService)
    val privilegedConfigRoute = Http4sServerInterpreter[IO]().toRoutes(
      Endpoints.privilegedConfigEndpoint.serverLogic {
        case (
              remoteAddress,
              allowInboundGroups,
              limit,
              hostCertIndex,
              ipFromPath,
              templateName
            ) =>
          privilegedConfigService.getConfig(
            remoteAddress,
            allowInboundGroups,
            limit,
            hostCertIndex,
            ipFromPath,
            templateName
          )
      }
    )

    val request = Request[IO](
      method = Method.GET,
      uri = uri"/privileged/config/192.168.1.1/default"
    ).withAttribute(
      Request.Keys.ConnectionInfo,
      Request.Connection(
        local = SocketAddress.fromInetSocketAddress(
          new java.net.InetSocketAddress("localhost", 8080)
        ),
        remote = SocketAddress.fromInetSocketAddress(
          new java.net.InetSocketAddress("192.168.1.100", 12345)
        ),
        secure = false
      )
    )

    val response = privilegedConfigRoute.orNotFound.run(request)

    assertIO(response.map(_.status), Status.Ok) *>
      assertIO(response.flatMap(_.as[String]), "config content")
  }

  test(
    "Privileged endpoint should return 403 Forbidden for non-privileged IP"
  ) {
    val fileSystem = new FileSystem {
      override def read(path: String): IO[String] = IO.pure("config content")
      override def list(
          path: fs2.io.file.Path
      ): fs2.Stream[IO, fs2.io.file.Path] = fs2.Stream.empty
      override def exists(path: fs2.io.file.Path): IO[Boolean] = IO.pure(true)
      override def validatePath(
          path: fs2.io.file.Path,
          basePath: fs2.io.file.Path
      ): IO[Unit] = IO.unit
    }
    // Create a mock TemplateService that uses the mock FileSystem
    val mockTemplateService = new TemplateService(config, fileSystem) {
      override def getConfig(
          templateName: String,
          clientIp: Option[java.net.InetSocketAddress],
          limit: Option[Int],
          hostCertIndex: Option[Int],
          allowInboundGroups: Option[List[String]]
      ): IO[Either[HttpError, String]] =
        IO.pure(Right("config content")) // Return the expected content
    }
    val privilegedConfigService =
      new PrivilegedConfigService(config, mockTemplateService)
    val privilegedConfigRoute = Http4sServerInterpreter[IO]().toRoutes(
      Endpoints.privilegedConfigEndpoint.serverLogic {
        case (
              remoteAddress,
              allowInboundGroups,
              limit,
              ipFromPath,
              hostCertIndex,
              templateName
            ) =>
          privilegedConfigService.getConfig(
            remoteAddress,
            allowInboundGroups,
            limit,
            ipFromPath,
            hostCertIndex,
            templateName
          )
      }
    )
    val request = Request[IO](
      method = Method.GET,
      uri = uri"/privileged/config/192.168.1.1/default"
    ).withAttribute(
      Request.Keys.ConnectionInfo,
      Request.Connection(
        local = SocketAddress.fromInetSocketAddress(
          new java.net.InetSocketAddress("localhost", 8080)
        ),
        remote = SocketAddress.fromInetSocketAddress(
          new java.net.InetSocketAddress("1.2.3.4", 12345)
        ),
        secure = false
      )
    )

    val response = privilegedConfigRoute.orNotFound.run(request)

    assertIO(response.map(_.status), Status.Forbidden)
  }

  test(
    "Privileged endpoint should return 404 Not Found for non-existent config"
  ) {
    val fileSystem = new FileSystem {
      override def read(path: String): IO[String] =
        IO.raiseError(new java.nio.file.NoSuchFileException(path))
      override def list(
          path: fs2.io.file.Path
      ): fs2.Stream[IO, fs2.io.file.Path] = fs2.Stream.empty
      override def exists(path: fs2.io.file.Path): IO[Boolean] = IO.pure(false)
      override def validatePath(
          path: fs2.io.file.Path,
          basePath: fs2.io.file.Path
      ): IO[Unit] = IO.unit
    }
    // Create a mock TemplateService that uses the mock FileSystem
    val mockTemplateService = new TemplateService(config, fileSystem) {
      override def getConfig(
          templateName: String,
          clientIp: Option[java.net.InetSocketAddress],
          limit: Option[Int],
          hostCertIndex: Option[Int],
          allowInboundGroups: Option[List[String]]
      ): IO[Either[HttpError, String]] =
        IO.pure(
          Left(
            HttpError.NotFound(
              "Configuration for host 'unknownhost' not found."
            )
          )
        ) // Simulate 404
    }
    val privilegedConfigService =
      new PrivilegedConfigService(config, mockTemplateService)
    val privilegedConfigRoute = Http4sServerInterpreter[IO]().toRoutes(
      Endpoints.privilegedConfigEndpoint.serverLogic {
        case (
              remoteAddress,
              allowInboundGroups,
              limit,
              hostCertIndex,
              ipFromPath,
              templateName
            ) =>
          privilegedConfigService.getConfig(
            remoteAddress,
            allowInboundGroups,
            limit,
            hostCertIndex,
            ipFromPath,
            templateName
          )
      }
    )
    val request = Request[IO](
      method = Method.GET,
      uri = uri"/privileged/config/192.168.1.1/default"
    ).withAttribute(
      Request.Keys.ConnectionInfo,
      Request.Connection(
        local = SocketAddress.fromInetSocketAddress(
          new java.net.InetSocketAddress("localhost", 8080)
        ),
        remote = SocketAddress.fromInetSocketAddress(
          new java.net.InetSocketAddress("192.168.1.100", 12345)
        ),
        secure = false
      )
    )

    val response = privilegedConfigRoute.orNotFound.run(request)

    assertIO(response.map(_.status), Status.NotFound)
  }

  test("Unified config endpoint should handle allow_inbound_groups parameter") {
    val expectedGroups = List("testgroup1", "testgroup2")
    val fileSystem = new FileSystem {
      override def read(path: String): IO[String] = IO.pure("config content")
      override def list(
          path: fs2.io.file.Path
      ): fs2.Stream[IO, fs2.io.file.Path] = fs2.Stream.empty
      override def exists(path: fs2.io.file.Path): IO[Boolean] = IO.pure(true)
      override def validatePath(
          path: fs2.io.file.Path,
          basePath: fs2.io.file.Path
      ): IO[Unit] = IO.unit
    }

    val mockTemplateService = new TemplateService(config, fileSystem) {
      override def getConfig(
          templateName: String,
          clientIp: Option[java.net.InetSocketAddress],
          limit: Option[Int],
          hostCertIndex: Option[Int],
          allowInboundGroups: Option[List[String]]
      ): IO[Either[HttpError, String]] = {
        assertEquals(templateName, "default")
        assertEquals(allowInboundGroups, Some(expectedGroups))
        IO.pure(Right("config content"))
      }
    }

    val unifiedRoute = Http4sServerInterpreter[IO]().toRoutes(
      Endpoints.unifiedTemplateEndpoint.serverLogic {
        case (
              remoteAddress,
              allowInboundGroups,
              limit,
              hostCertIndex,
              templateName
            ) =>
          mockTemplateService.getConfig(
            templateName,
            remoteAddress,
            limit,
            hostCertIndex,
            allowInboundGroups
          )
      }
    )

    val request = Request[IO](
      method = Method.GET,
      uri = uri"/config/default?allow_inbound_groups=testgroup1,testgroup2"
    )

    val response = unifiedRoute.orNotFound.run(request)
    assertIO(response.map(_.status), Status.Ok) *>
      assertIO(response.flatMap(_.as[String]), "config content")
  }

  test(
    "Unified template endpoint should return template content for a valid template"
  ) {
    val fileSystem = new FileSystem {
      override def read(path: String): IO[String] = IO.pure("template content")
      override def list(
          path: fs2.io.file.Path
      ): fs2.Stream[IO, fs2.io.file.Path] = fs2.Stream.empty
      override def exists(path: fs2.io.file.Path): IO[Boolean] = IO.pure(true)
      override def validatePath(
          path: fs2.io.file.Path,
          basePath: fs2.io.file.Path
      ): IO[Unit] = IO.unit
    }
    val mockTemplateService = new TemplateService(config, fileSystem) {
      override def getConfig(
          templateName: String,
          clientIp: Option[java.net.InetSocketAddress],
          limit: Option[Int],
          hostCertIndex: Option[Int],
          allowInboundGroups: Option[List[String]]
      ): IO[Either[HttpError, String]] =
        IO.pure(
          Right("this is the mock content")
        ) // Return the expected content
    }

    val unifiedTemplateRoute = Http4sServerInterpreter[IO]().toRoutes(
      Endpoints.unifiedTemplateEndpoint.serverLogic {
        case (
              remoteAddress,
              allowInboundGroups,
              limit,
              hostCertIndex,
              templateName
            ) =>
          mockTemplateService.getConfig(
            templateName,
            remoteAddress,
            limit,
            hostCertIndex,
            allowInboundGroups
          )
      }
    )

    val request = Request[IO](method = Method.GET, uri = uri"/config/default")
      .withAttribute(
        Request.Keys.ConnectionInfo,
        Request.Connection(
          local = SocketAddress.fromInetSocketAddress(
            new java.net.InetSocketAddress("localhost", 8080)
          ),
          remote = SocketAddress.fromInetSocketAddress(
            new java.net.InetSocketAddress("1.2.3.4", 12345)
          ),
          secure = false
        )
      )
    val response = unifiedTemplateRoute.orNotFound.run(request)

    assertIO(response.map(_.status), Status.Ok) *>
      assertIO(response.flatMap(_.as[String]), "this is the mock content")
  }

  test(
    "Unified template endpoint should return 404 for path traversal attempt"
  ) {
    val fileSystem = new FileSystem {
      override def read(path: String): IO[String] = IO.pure("")
      override def list(
          path: fs2.io.file.Path
      ): fs2.Stream[IO, fs2.io.file.Path] = fs2.Stream.empty
      override def exists(path: fs2.io.file.Path): IO[Boolean] = IO.pure(false)
      override def validatePath(
          path: fs2.io.file.Path,
          basePath: fs2.io.file.Path
      ): IO[Unit] = IO.unit
    }
    val mockTemplateService = new TemplateService(config, fileSystem) {
      override def getConfig(
          templateName: String,
          clientIp: Option[java.net.InetSocketAddress],
          limit: Option[Int],
          hostCertIndex: Option[Int],
          allowInboundGroups: Option[List[String]]
      ): IO[Either[HttpError, String]] =
        IO.pure(Left(HttpError.NotFound("Invalid template name provided.")))
    }

    val unifiedTemplateRoute = Http4sServerInterpreter[IO]().toRoutes(
      Endpoints.unifiedTemplateEndpoint.serverLogic {
        case (
              remoteAddress,
              allowInboundGroups,
              limit,
              hostCertIndex,
              templateName
            ) =>
          mockTemplateService.getConfig(
            templateName,
            remoteAddress,
            limit,
            hostCertIndex,
            allowInboundGroups
          )
      }
    )

    // URL-encoded "../"
    val request =
      Request[IO](method = Method.GET, uri = uri"/config/..%2f..%2fsecret")
    val response = unifiedTemplateRoute.orNotFound.run(request)

    assertIO(response.map(_.status), Status.NotFound)
  }
  test("Unified config endpoint should handle templateName") {
    val fileSystem = new FileSystem {
      override def read(path: String): IO[String] = IO.pure("config content")
      override def list(
          path: fs2.io.file.Path
      ): fs2.Stream[IO, fs2.io.file.Path] = fs2.Stream.empty
      override def exists(path: fs2.io.file.Path): IO[Boolean] = IO.pure(true)
      override def validatePath(
          path: fs2.io.file.Path,
          basePath: fs2.io.file.Path
      ): IO[Unit] = IO.unit
    }

    val mockTemplateService = new TemplateService(config, fileSystem) {
      override def getConfig(
          templateName: String,
          clientIp: Option[java.net.InetSocketAddress],
          limit: Option[Int],
          hostCertIndex: Option[Int],
          allowInboundGroups: Option[List[String]]
      ): IO[Either[HttpError, String]] = {
        assertEquals(templateName, "default")
        IO.pure(Right("mock config content"))
      }
    }

    val unifiedRoute = Http4sServerInterpreter[IO]().toRoutes(
      Endpoints.unifiedTemplateEndpoint.serverLogic {
        case (
              remoteAddress,
              allowInboundGroups,
              limit,
              hostCertIndex,
              templateName
            ) =>
          mockTemplateService.getConfig(
            templateName,
            remoteAddress,
            limit,
            hostCertIndex,
            allowInboundGroups
          )
      }
    )

    val request = Request[IO](
      method = Method.GET,
      uri = uri"/config/default"
    )

    val response = unifiedRoute.orNotFound.run(request)
    assertIO(response.map(_.status), Status.Ok) *>
      assertIO(response.flatMap(_.as[String]), "mock config content")
  }
}
