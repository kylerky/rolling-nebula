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

import cats.data.EitherT
import java.nio.file.NoSuchFileException

class TemplateService(config: ConfigServerConfig, fileSystem: FileSystem) {
  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private val templatesDir = Path(config.templatesDir)
  private val baseConfigDir = Path(config.configDir)

  def getTemplate(templateName: String): IO[Either[HttpError, String]] = {
    if (
      templateName.contains("..") || templateName.contains("/") || templateName
        .contains("\\")
    ) {
      return IO.pure(Left(HttpError.NotFound("Invalid template name provided.")))
    }

    val requestedPath = templatesDir / s"$templateName.yaml"

    val result: EitherT[IO, HttpError, String] = for {
      exists <- EitherT.right(fileSystem.exists(requestedPath))
      _ <- EitherT.cond[IO](
        exists,
        (),
        HttpError.NotFound(s"Template '$templateName' not found.")
      )
      _ <- EitherT(
        fileSystem
          .validatePath(requestedPath, templatesDir)
          .map(_ => Right(()))
          .handleErrorWith(e =>
            IO.pure(
              Left(
                HttpError.NotFound("Invalid template name (path traversal attempt).")
              )
            )
          )
      )
      content <- EitherT.right(fileSystem.read(requestedPath.toString))
    } yield content
    result.value
  }

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

  def getConfig(
      templateName: String,
      clientIp: Option[InetSocketAddress],
      limit: Option[Int],
      allowInboundGroups: Option[List[String]]
  ): IO[Either[HttpError, String]] = {
    val result: EitherT[IO, HttpError, String] = for {
      ip <- EitherT.fromOption(
        clientIp.map(_.getAddress.getHostAddress),
        HttpError.InternalServerError("Could not determine client IP address.")
      )
      templateContent <- EitherT(getTemplate(templateName))
      baseJson <- EitherT.fromEither[IO](
        parser
          .parse(templateContent)
          .leftMap(err =>
            HttpError.InternalServerError(
              s"Failed to parse YAML template '$templateName': ${err.getMessage}"
            )
          )
      )
      latestDirsStream = getLatestConfigDirs(limit)
      (caCerts, hostCerts) <- EitherT.right(
        (
          streamCertContents(latestDirsStream, Path("ca.crt")).compile.string,
          streamCertContents(
            latestDirsStream,
            Path("certs") / s"$ip.crt"
          ).compile.string
        ).parTupled
      )
      _ <- EitherT.right(
        if (hostCerts.isEmpty)
          logger.warn(s"No certificates found for IP: $ip")
        else IO.unit
      )
      jsonWithFirewallRules = allowInboundGroups.getOrElse(
        List.empty
      ) match {
        case Nil    => baseJson
        case groups =>
          val newRules = groups.map { groupName =>
            Json.obj(
              "port" -> Json.fromString("any"),
              "proto" -> Json.fromString("any"),
              "groups" -> Json.fromValues(List(Json.fromString(groupName)))
            )
          }
          baseJson.hcursor
            .downField("firewall")
            .downField("inbound")
            .withFocus(inbound =>
              Json.fromValues(
                inbound.asArray.getOrElse(Vector.empty) ++ newRules
              )
            )
            .top
            .getOrElse(baseJson)
      }
      pkiJson = Json.obj(
        "pki" -> Json.obj(
          "ca" -> Json.fromString(caCerts),
          "cert" -> Json.fromString(hostCerts),
          "key" -> Json.fromString(config.pkiKeyPath)
        )
      )
      finalJson = jsonWithFirewallRules
        .deepMerge(pkiJson)
    } yield printer.print(finalJson)
    result.value
  }
}
