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

    val result = service.getConfig("default", clientIp, None, None)

    result.flatMap {
      case Right(yamlString) =>
        IO {
          assert(yamlString.contains("pki:"), "YAML should contain pki section")
          assert(
            yamlString.contains("proto: icmp"),
            "YAML should contain icmp firewall rule"
          )
        }
      case Left(e) =>
        IO(fail(s"Expected a valid config, but got an error: $e"))
    }
  }

  test("getConfig should add inbound firewall rules for allow_inbound_groups") {
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
    val groups = Some(List("group1", "group2"))

    val result = service.getConfig("default", clientIp, None, groups)

    result.flatMap {
      case Right(yamlString) =>
        import io.circe.yaml.parser
        import io.circe.Json

        val parsedYaml = parser.parse(yamlString).getOrElse(Json.Null)
        val inboundRules = parsedYaml.hcursor
          .downField("firewall")
          .downField("inbound")
          .as[List[Json]]
          .getOrElse(List.empty)

        IO {
          assertEquals(inboundRules.size, 3) // icmp rule + 2 new group rules
          val groupRules =
            inboundRules.filter(rule => rule.hcursor.downField("groups").succeeded)
          assertEquals(groupRules.size, 2)
          val groupNames = groupRules.flatMap(
            _.hcursor.downField("groups").as[List[String]].getOrElse(List.empty)
          )
          assertEquals(groupNames, List("group1", "group2"))
        }
      case Left(e) =>
        IO(fail(s"Expected a valid config, but got an error: $e"))
    }
  }

  test("getTemplate should return NotFound for path traversal attempts") {
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
    val service = new TemplateService(mockConfig, new MockFileSystem)

    // Attempt to traverse up from the templatesDir
    val result = service.getTemplate("../secret.txt")

    result.flatMap {
      case Left(HttpError.NotFound(msg)) =>
        IO(
          assert(
            msg.contains("Invalid template name"),
            "Error message should indicate an invalid template name"
          )
        )
      case other =>
        IO(fail(s"Expected NotFound, but got $other"))
    }
  }
}
