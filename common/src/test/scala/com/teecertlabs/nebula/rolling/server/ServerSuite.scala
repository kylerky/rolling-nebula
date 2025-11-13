package com.teecertlabs.nebula.rolling.server

import munit.CatsEffectSuite
import cats.effect.IO
import com.teecertlabs.nebula.rolling.ConfigServerConfig
import com.teecertlabs.nebula.rolling.FileSystem
import org.http4s._
import org.http4s.implicits._
import sttp.tapir.server.http4s.Http4sServerInterpreter
import java.net.InetSocketAddress

class ServerSuite extends CatsEffectSuite {

  val config = ConfigServerConfig(
    host = "localhost",
    port = 8080,
    templatePath = "",
    pkiKeyPath = "",
    configDir = "configs",
    labServerInboundGroups = List.empty,
    privilegedIps = List("192.168.1.100")
  )

  test("Privileged endpoint should return 200 OK for privileged IP") {
    val fileSystem = new FileSystem {
      override def read(path: String): IO[String] = IO.pure("config content")
    }
    val privilegedConfigService =
      new PrivilegedConfigService(config, fileSystem)
    val privilegedConfigRoute = Http4sServerInterpreter[IO]().toRoutes(
      Endpoints.privilegedConfigEndpoint.serverLogic {
        case (hostname, remoteAddress) =>
          privilegedConfigService.getConfig(remoteAddress, hostname)
      }
    )

    val request = Request[IO](
      method = Method.GET,
      uri = uri"/privileged/config/myhost"
    ).withAttribute(
      Request.Keys.ConnectionInfo,
      Request.Connection(
        local = InetSocketAddress.createUnresolved("localhost", 8080),
        remote = InetSocketAddress.createUnresolved("192.168.1.100", 12345),
        secure = false
      )
    )

    val response = privilegedConfigRoute.orNotFound.run(request)

    assertIO(response.map(_.status), Status.Ok)
    assertIO(response.flatMap(_.as[String]), "config content")
  }

  test(
    "Privileged endpoint should return 403 Forbidden for non-privileged IP"
  ) {
    val fileSystem = new FileSystem {
      override def read(path: String): IO[String] = IO.pure("config content")
    }
    val privilegedConfigService =
      new PrivilegedConfigService(config, fileSystem)
    val privilegedConfigRoute = Http4sServerInterpreter[IO]().toRoutes(
      Endpoints.privilegedConfigEndpoint.serverLogic {
        case (hostname, remoteAddress) =>
          privilegedConfigService.getConfig(remoteAddress, hostname)
      }
    )

    val request = Request[IO](
      method = Method.GET,
      uri = uri"/privileged/config/myhost"
    ).withAttribute(
      Request.Keys.ConnectionInfo,
      Request.Connection(
        local = InetSocketAddress.createUnresolved("localhost", 8080),
        remote = InetSocketAddress.createUnresolved("1.2.3.4", 12345),
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
    }
    val privilegedConfigService =
      new PrivilegedConfigService(config, fileSystem)
    val privilegedConfigRoute = Http4sServerInterpreter[IO]().toRoutes(
      Endpoints.privilegedConfigEndpoint.serverLogic {
        case (hostname, remoteAddress) =>
          privilegedConfigService.getConfig(remoteAddress, hostname)
      }
    )

    val request = Request[IO](
      method = Method.GET,
      uri = uri"/privileged/config/unknownhost"
    ).withAttribute(
      Request.Keys.ConnectionInfo,
      Request.Connection(
        local = InetSocketAddress.createUnresolved("localhost", 8080),
        remote = InetSocketAddress.createUnresolved("192.168.1.100", 12345),
        secure = false
      )
    )

    val response = privilegedConfigRoute.orNotFound.run(request)

    assertIO(response.map(_.status), Status.NotFound)
  }
}
