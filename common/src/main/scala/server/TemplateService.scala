package com.teecertlabs.nebula.rolling.server

import com.teecertlabs.nebula.rolling.server.HttpError
import cats.effect.IO
import cats.implicits._
import com.teecertlabs.nebula.rolling.{ConfigServerConfig, FileSystem}
import fs2.io.file.{Files, Path}
import fs2.Stream
import io.circe.Json
import io.circe.yaml.parser
import io.circe.yaml.printer
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import java.net.InetSocketAddress

class TemplateService(config: ConfigServerConfig, fileSystem: FileSystem) {
  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private val templateFile = Path(config.templatePath)
  private val baseConfigDir = Path(config.configDir)

  private def loadTemplate: IO[Json] =
    fileSystem
      .read(config.templatePath)
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

  private def getLatestConfigDirs(limit: Option[Int]): Stream[IO, Path] = {
    val effectiveLimit = limit match {
      case Some(0)  => None // 0 means all
      case Some(-1) =>
        config.numConfigs match {
          case Some(0) => None
          case Some(n) => Some(n)
          case None    => Some(5) // Default if not specified in config
        }
      case Some(n) => Some(n)
      case None    =>
        config.numConfigs match {
          case Some(0) => None
          case Some(n) => Some(n)
          case None    => Some(5) // Default if not specified in config
        }
    }

    Stream
      .eval(
        fileSystem
          .list(baseConfigDir)
          .filter(p => p.fileName.toString.startsWith("config_"))
          .compile
          .toList
          .map { dirs =>
            val sortedDirs = dirs.sortBy(_.fileName.toString).reverse
            effectiveLimit match {
              case Some(n) => sortedDirs.take(n)
              case None    => sortedDirs
            }
          }
      )
      .flatMap(Stream.emits)
  }

  def streamCertContents(
      dirs: Stream[IO, Path],
      fileName: Path
  ): Stream[IO, String] =
    dirs
      .map(_ / fileName)
      .evalFilter(fileSystem.exists)
      .evalMap(path => fileSystem.read(path.toString))
      .intersperse("\n")

  private def generateConfig(
      clientIpStr: String,
      firewallType: String,
      limit: Option[Int]
  ): IO[String] = {
    val latestDirsStream = getLatestConfigDirs(limit)
    for {
      (caCerts, hostCerts, baseJson) <- (
        streamCertContents(latestDirsStream, Path("ca.crt")).compile.string,
        streamCertContents(
          latestDirsStream,
          Path("certs") / s"$clientIpStr.crt"
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
      firewallType: String,
      limit: Option[Int]
  ): IO[Either[HttpError, String]] = {
    clientIp.map(
      _.getAddress.getHostAddress
    ) match {
      case Some(ip) =>
        generateConfig(ip, firewallType, limit).attempt
          .map(
            _.leftMap(e => HttpError.InternalServerError(e.getMessage))
          )
      case None =>
        IO.pure(
          Left(
            HttpError.InternalServerError(
              "Could not determine client IP address."
            )
          )
        )
    }
  }

  def getLabServerConfig(
      clientIp: Option[InetSocketAddress],
      limit: Option[Int]
  ): IO[Either[HttpError, String]] =
    processRequest(clientIp, "lab_server", limit)

  def getDefaultConfig(
      clientIp: Option[InetSocketAddress],
      limit: Option[Int]
  ): IO[Either[HttpError, String]] =
    processRequest(clientIp, "default", limit)
}
