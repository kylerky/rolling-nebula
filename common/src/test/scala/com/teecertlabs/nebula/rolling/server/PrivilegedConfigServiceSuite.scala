package com.teecertlabs.nebula.rolling.server

import cats.effect.IO
import com.teecertlabs.nebula.rolling.{ConfigServerConfig, FileSystem}
import munit.CatsEffectSuite
import org.http4s.{Method, Request, Uri}
import fs2.io.file.Path
import java.net.InetAddress
import com.comcast.ip4s._

class PrivilegedConfigServiceSuite extends CatsEffectSuite {

  // Mock TemplateService for testing
  class MockTemplateService(config: ConfigServerConfig, fileSystem: FileSystem)
      extends TemplateService(config, fileSystem) {
    override def getConfig(
        templateName: String,
        clientIp: Option[java.net.InetSocketAddress],
        limit: Option[Int],
        hostCertIndex: Option[Int],
        allowInboundGroups: Option[List[String]]
    ): IO[Either[HttpError, String]] =
      IO.pure(
        Right(
          s"Mocked config content for IP: ${clientIp.map(_.getAddress.getHostAddress).getOrElse("N/A")}"
        )
      )
  }

  // Mock ConfigServerConfig for testing
  val mockConfig = ConfigServerConfig(
    host = "localhost",
    port = 8080,
    templatesDir = "/tmp/templates",
    configDir = "/tmp/configs",
    pkiKeyPath = "/tmp/pki.key",
    labServerInboundGroups = List.empty,
    privilegedIps = List("127.0.0.1"), // Add a privileged IP for testing Auth
    numConfigs = Some(5)
  )

  // Mock FileSystem for testing (needed by MockTemplateService)
  class MockFileSystem extends FileSystem {
    override def read(path: String): IO[String] =
      IO.pure(s"Mock content for $path")
    override def list(
        path: fs2.io.file.Path
    ): fs2.Stream[IO, fs2.io.file.Path] = fs2.Stream.empty
    override def exists(path: fs2.io.file.Path): IO[Boolean] = IO.pure(false)
    override def validatePath(path: Path, basePath: Path): IO[Unit] = IO.unit
  }
  val mockFileSystem = new MockFileSystem()

  test(
    "PrivilegedConfigService should return config from TemplateService for a privileged IP"
  ) {
    val mockTemplateService =
      new MockTemplateService(mockConfig, mockFileSystem)
    val service = new PrivilegedConfigService(mockConfig, mockTemplateService)

    val testIp = ip"192.168.1.1"
    // Simulate a privileged remote address
    val privilegedRemoteAddress =
      Some(new java.net.InetSocketAddress("127.0.0.1", 12345))

    // The route in Endpoints.scala will extract the IP from the path and pass it to the service
    // For this unit test, we directly call the service's getConfig method
    service
      .getConfig(
        privilegedRemoteAddress,
        None,
        None,
        None,
        testIp.toInetAddress,
        "default"
      )
      .flatMap {
        case Right(yamlString) =>
          IO(
            assertEquals(
              yamlString,
              s"Mocked config content for IP: ${testIp.toInetAddress.getHostAddress}"
            )
          )
        case Left(e) =>
          IO(fail(s"Expected a valid config, but got an error: $e"))
      }
  }

  test(
    "PrivilegedConfigService should return Unauthorized for a non-privileged IP"
  ) {
    val mockTemplateService =
      new MockTemplateService(mockConfig, mockFileSystem)
    val service = new PrivilegedConfigService(mockConfig, mockTemplateService)

    val testIp = ip"192.168.1.1"
    // Simulate a non-privileged remote address
    val nonPrivilegedRemoteAddress =
      Some(new java.net.InetSocketAddress("10.0.0.1", 12345))

    service
      .getConfig(
        nonPrivilegedRemoteAddress,
        None,
        None,
        None,
        testIp.toInetAddress,
        "default"
      )
      .flatMap {
        case Right(_) =>
          IO(fail("Expected Unauthorized error, but got a valid config"))
        case Left(HttpError.Unauthorized(msg)) =>
          IO(assertEquals(msg, "Forbidden"))
        case Left(e) =>
          IO(fail(s"Expected Unauthorized error, but got unexpected error: $e"))
      }
  }
  test(
    "PrivilegedConfigService should pass allowInboundGroups to TemplateService"
  ) {
    val expectedGroups = Some(List("privileged-group"))

    val mockTemplateService =
      new MockTemplateService(mockConfig, mockFileSystem) {
        override def getConfig(
            templateName: String,
            clientIp: Option[java.net.InetSocketAddress],
            limit: Option[Int],
            hostCertIndex: Option[Int],
            allowInboundGroups: Option[List[String]]
        ): IO[Either[HttpError, String]] = {
          assertEquals(allowInboundGroups, expectedGroups)
          IO.pure(Right("mock content"))
        }
      }
    val service = new PrivilegedConfigService(mockConfig, mockTemplateService)

    val testIp = ip"192.168.1.1"
    val privilegedRemoteAddress =
      Some(new java.net.InetSocketAddress("127.0.0.1", 12345))

    service
      .getConfig(
        privilegedRemoteAddress,
        expectedGroups,
        None,
        None,
        testIp.toInetAddress,
        "default"
      )
      .flatMap {
        case Right(content) =>
          IO(assertEquals(content, "mock content"))
        case Left(e) =>
          IO(fail(s"Expected a valid config, but got an error: $e"))
      }
  }
}
