package com.nebula.rolling.server

import cats.effect.IO
import cats.implicits._
import com.nebula.rolling.ConfigServerConfig
import fs2.io.file.{Files, Path}
import fs2.Stream
import io.circe.Json
import io.circe.yaml.parser
import io.circe.yaml.printer
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import java.net.InetSocketAddress

class TemplateService(config: ConfigServerConfig) {
  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private val templateFile = Path(config.templatePath)
  private val baseConfigDir = Path(config.configDir)

  private def loadTemplate: IO[Json] =
    Files[IO]
      .readAll(templateFile)
      .through(fs2.text.utf8.decode)
      .compile
      .string
      .flatMap(content =>
        IO.fromEither(
          parser
            .parse(content)
            .leftMap(err =>
              new RuntimeException(
                s"Failed to parse YAML template: ${err.getMessage}"
              )
            )
        )
      )

  private def getLatestConfigDirs: Stream[IO, Path] =
    Stream
      .eval(
        Files[IO]
          .list(baseConfigDir)
          .filter(p => p.fileName.toString.startsWith("config_"))
          .compile
          .toList
          .map(_.sortBy(_.fileName.toString).reverse.take(5))
      )
      .flatMap(Stream.emits)

  private def streamCertContents(
      dirs: Stream[IO, Path],
      fileName: String
  ): Stream[IO, String] =
    dirs
      .map(_ / "certs" / fileName)
      .evalFilter(Files[IO].exists)
      .flatMap(path => Files[IO].readAll(path).through(fs2.text.utf8.decode))
      .intersperse("\n")

  private def generateConfig(
      clientIpStr: String,
      firewallType: String
  ): IO[String] = {
    val latestDirsStream = getLatestConfigDirs
    for {
      (caCerts, hostCerts, baseJson) <- (
        streamCertContents(latestDirsStream, "ca.crt").compile.string,
        streamCertContents(
          latestDirsStream,
          s"$clientIpStr.crt"
        ).compile.string,
        loadTemplate
      ).parTupled
      _ <-
        if (hostCerts.isEmpty)
          logger.warn(s"No certificates found for IP: $clientIpStr")
        else IO.unit

      icmpRule = Json.obj(
        "proto" -> Json.fromString("icmp"),
        "port" -> Json.fromString("any"),
        "host" -> Json.fromString("any")
      )

      firewallJson = firewallType match {
        case "lab_server" =>
          val groupRule = Json.obj(
            "proto" -> Json.fromString("any"),
            "groups" -> Json.fromValues(
              config.labServerInboundGroups.map(Json.fromString)
            )
          )
          Json.obj("inbound" -> Json.arr(icmpRule, groupRule))
        case "default" =>
          Json.obj("inbound" -> Json.arr(icmpRule))
      }

      outboundFirewall = Json.obj(
        "outbound" -> Json.arr(
          Json.obj(
            "port" -> Json.fromString("any"),
            "proto" -> Json.fromString("any"),
            "host" -> Json.fromString("any")
          )
        )
      )

      pkiJson = Json.obj(
        "pki" -> Json.obj(
          "ca" -> Json.fromString(caCerts),
          "cert" -> Json.fromString(hostCerts),
          "key" -> Json.fromString(config.pkiKeyPath)
        )
      )

      finalJson = baseJson
        .deepMerge(pkiJson)
        .deepMerge(firewallJson)
        .deepMerge(outboundFirewall)
    } yield printer.print(finalJson)
  }

  private def processRequest(
      clientIp: Option[InetSocketAddress],
      firewallType: String
  ): IO[Either[String, String]] = {
    clientIp.map(_.ip.toString) match {
      case Some(ip) =>
        generateConfig(ip, firewallType).attempt.map(_.leftMap(_.getMessage))
      case None => IO.pure(Left("Could not determine client IP address."))
    }
  }

  def getLabServerConfig(
      clientIp: Option[InetSocketAddress]
  ): IO[Either[String, String]] =
    processRequest(clientIp, "lab_server")

  def getDefaultConfig(
      clientIp: Option[InetSocketAddress]
  ): IO[Either[String, String]] =
    processRequest(clientIp, "default")
}
