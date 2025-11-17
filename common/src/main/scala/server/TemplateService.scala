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
    def getRealPath(p: Path): IO[Either[HttpError, java.nio.file.Path]] =
      IO.blocking(p.toNioPath.toRealPath()).attempt.map {
        case Right(path) => Right(path)
        case Left(_: NoSuchFileException) =>
          Left(HttpError.NotFound(s"Template '$templateName' not found."))
        case Left(e) =>
          Left(
            HttpError.InternalServerError(
              s"Could not resolve path: ${e.getMessage}"
            )
          )
      }

    val result: EitherT[IO, HttpError, String] = for {
      _ <- EitherT.cond[IO](
        !templateName.contains("..") && !templateName.contains("/") && !templateName
          .contains("\\"),
        (),
        HttpError.NotFound("Invalid template name provided.")
      )
      requestedPath = templatesDir / s"$templateName.yaml"
      realTemplatesDir  <- EitherT(getRealPath(templatesDir))
      realRequestedPath <- EitherT(getRealPath(requestedPath))
      _ <- EitherT.cond[IO](
        realRequestedPath.startsWith(realTemplatesDir),
        (),
        HttpError.NotFound("Invalid template name (path traversal attempt).")
      )
      content <- EitherT(
        fileSystem.read(requestedPath.toString).map(Right(_))
      )
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
    clientIp.map(
      _.getAddress.getHostAddress
    ) match {
      case Some(ip) =>
        val latestDirsStream = getLatestConfigDirs(limit)
        val templateFilePath = templatesDir / s"$templateName.yaml"

        (for {
          (caCerts, hostCerts, baseJson) <- (
            streamCertContents(latestDirsStream, Path("ca.crt")).compile.string,
            streamCertContents(
              latestDirsStream,
              Path("certs") / s"$ip.crt"
            ).compile.string,
            fileSystem
              .read(templateFilePath.toString)
              .flatMap(content =>
                IO.fromEither(
                  parser
                    .parse(content)
                    .leftMap(err =>
                      new RuntimeException(
                        s"Failed to parse YAML template '$templateName': ${err.getMessage}"
                      )
                    )
                )
              )
          ).parTupled
          _ <-
            if (hostCerts.isEmpty)
              logger.warn(s"No certificates found for IP: $ip")
            else IO.unit

          // Add dynamic firewall rules
          jsonWithFirewallRules = allowInboundGroups.getOrElse(List.empty) match {
            case Nil => baseJson
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
                .withFocus(inbound => Json.fromValues(inbound.asArray.getOrElse(Vector.empty) ++ newRules))
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
        } yield printer.print(finalJson)).attempt
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
  }}
