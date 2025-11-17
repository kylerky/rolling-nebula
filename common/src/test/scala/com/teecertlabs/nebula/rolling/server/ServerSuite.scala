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
    labServerInboundGroups = List.empty,
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
    }
    // Create a mock TemplateService that uses the mock FileSystem
    val mockTemplateService = new TemplateService(config, fileSystem) {
      override def getConfig(
          templateName: String,
          clientIp: Option[java.net.InetSocketAddress],
          limit: Option[Int],
          allowInboundGroups: Option[List[String]]
      ): IO[Either[HttpError, String]] =
        IO.pure(Right("config content")) // Return the expected content
    }
    val privilegedConfigService =
      new PrivilegedConfigService(config, mockTemplateService)
    val privilegedConfigRoute = Http4sServerInterpreter[IO]().toRoutes(
      Endpoints.privilegedConfigEndpoint.serverLogic {
        case (remoteAddress, allowInboundGroups, limit, ipFromPath) =>
          privilegedConfigService.getConfig(remoteAddress, allowInboundGroups, limit, ipFromPath)
      }
    )

    val request = Request[IO](
      method = Method.GET,
      uri = uri"/privileged/config/192.168.1.1"
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
    }
    // Create a mock TemplateService that uses the mock FileSystem
    val mockTemplateService = new TemplateService(config, fileSystem) {
      override def getConfig(
          templateName: String,
          clientIp: Option[java.net.InetSocketAddress],
          limit: Option[Int],
          allowInboundGroups: Option[List[String]]
      ): IO[Either[HttpError, String]] =
        IO.pure(Right("config content")) // Return the expected content
    }
    val privilegedConfigService =
      new PrivilegedConfigService(config, mockTemplateService)
    val privilegedConfigRoute = Http4sServerInterpreter[IO]().toRoutes(
      Endpoints.privilegedConfigEndpoint.serverLogic {
        case (remoteAddress, allowInboundGroups, limit, ipFromPath) =>
          privilegedConfigService.getConfig(remoteAddress, allowInboundGroups, limit, ipFromPath)
      }
    )
    val request = Request[IO](
      method = Method.GET,
      uri = uri"/privileged/config/192.168.1.1"
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
    }
    // Create a mock TemplateService that uses the mock FileSystem
    val mockTemplateService = new TemplateService(config, fileSystem) {
      override def getConfig(
          templateName: String,
          clientIp: Option[java.net.InetSocketAddress],
          limit: Option[Int],
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
        case (remoteAddress, allowInboundGroups, limit, ipFromPath) =>
          privilegedConfigService.getConfig(remoteAddress, allowInboundGroups, limit, ipFromPath)
      }
    )
    val request = Request[IO](
      method = Method.GET,
      uri = uri"/privileged/config/192.168.1.1"
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
  
  test(
    "Default endpoint should handle allow_inbound_groups parameter"
  ) {
    val expectedGroups = List("testgroup1", "testgroup2")
    val fileSystem = new FileSystem {
      override def read(path: String): IO[String] = IO.pure("config content")
      override def list(
          path: fs2.io.file.Path
      ): fs2.Stream[IO, fs2.io.file.Path] = fs2.Stream.empty
      override def exists(path: fs2.io.file.Path): IO[Boolean] = IO.pure(true)
    }

    val mockTemplateService = new TemplateService(config, fileSystem) {
      override def getConfig(
          templateName: String,
          clientIp: Option[java.net.InetSocketAddress],
          limit: Option[Int],
          allowInboundGroups: Option[List[String]]
      ): IO[Either[HttpError, String]] = {
        assertEquals(templateName, "default")
        assertEquals(allowInboundGroups, Some(expectedGroups))
        IO.pure(Right("config content"))
      }
    }

    val defaultRoute = Http4sServerInterpreter[IO]().toRoutes(
      Endpoints.defaultEndpoint.serverLogic {
        case (remoteAddress, allowInboundGroups, limit) =>
          mockTemplateService.getConfig("default", remoteAddress, limit, allowInboundGroups)
      }
    )

    val request = Request[IO](
      method = Method.GET,
      uri = uri"/config/default?allow_inbound_groups=testgroup1,testgroup2"
    )

    val response = defaultRoute.orNotFound.run(request)
    assertIO(response.map(_.status), Status.Ok) *>
    assertIO(response.flatMap(_.as[String]), "config content")
  }
}
