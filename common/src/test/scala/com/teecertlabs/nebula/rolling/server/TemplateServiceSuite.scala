package com.teecertlabs.nebula.rolling.server

import cats.effect.IO
import com.teecertlabs.nebula.rolling.{ConfigServerConfig, FileSystem}
import fs2.Stream
import fs2.io.file.Path
import munit.CatsEffectSuite

class TemplateServiceSuite extends CatsEffectSuite {

  // Mock FileSystem for testing
  class MockFileSystem extends FileSystem {
    override def read(path: String): IO[String] =
      IO.pure(s"Mock content for $path")
    override def list(path: Path): Stream[IO, Path] = Stream.empty
    override def exists(path: Path): IO[Boolean] = IO.pure(false)
  }

  test("getConfig should return a valid YAML configuration for default template") {
    val defaultTemplateContent = """
pki:
  ca: ""
  cert: ""
  key: ""
firewall:
  inbound:
    - proto: icmp
      port: any
      host: any
  outbound:
    - port: any
      proto: any
      host: any
"""
    val mockFileSystem = new MockFileSystem {
      override def read(path: String): IO[String] = path match {
        case "/tmp/templates/default.yaml" => IO.pure(defaultTemplateContent)
        case _                             => IO.pure(s"Mock content for $path")
      }
      override def list(path: Path): Stream[IO, Path] = Stream.empty
      override def exists(path: Path): IO[Boolean] = IO.pure(true)
    }

    val mockConfig = ConfigServerConfig(
      host = "localhost",
      port = 8080,
      templatesDir = "/tmp/templates",
      configDir = "/tmp/configs",
      pkiKeyPath = "/tmp/pki.key",
      labServerInboundGroups = List.empty,
      privilegedIps = List.empty,
      numConfigs = Some(5)
    )

    val service = new TemplateService(mockConfig, mockFileSystem)
    val clientIp = Some(new java.net.InetSocketAddress("1.2.3.4", 12345))

    val result = service.getConfig("default", clientIp, None)

    result.flatMap {
      case Right(yamlString) =>
        IO(
          assert(yamlString.contains("pki:"), "YAML should contain pki section")
        )
        IO(
          assert(yamlString.contains("proto: icmp"), "YAML should contain icmp firewall rule")
        )
      case Left(e) =>
        e match {
          case HttpError.InternalServerError(msg) =>
            IO(fail(s"Expected a valid config, but got an error: $msg"))
          case other =>
            IO(fail(s"Unexpected error: $other"))
        }
    }
  }
}
