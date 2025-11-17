package com.teecertlabs.nebula.rolling

import cats.effect.IO
import cats.implicits._
import fs2.io.file.{Files, Path}
import scala.sys.process._
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object NebulaCert {

  def generateCA(baseCaName: String, outputDir: Path): IO[Unit] = IO {
    val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    val today = LocalDate.now().format(dateFormatter)
    val caName = s"$baseCaName-$today"

    val command = Seq(
      "nebula-cert",
      "ca",
      "-name",
      caName,
      "-out-crt",
      (outputDir / "ca.crt").toString,
      "-out-key",
      (outputDir / "ca.key").toString
    )
    val result = command.!!
    println(s"CA generation result: $result")
  }

  def sign(
      name: String,
      inPub: Path,
      networks: List[String],
      groups: List[String],
      caCrt: Path,
      caKey: Path,
      outCrt: Path,
      unsafeNetworks: Option[List[String]]
  ): Seq[String] = {
    val baseCommand = Seq(
      "nebula-cert",
      "sign",
      "-name",
      name,
      "-in-pub",
      inPub.toString,
      "-networks",
      networks.mkString(","),
      "-groups",
      groups.mkString(","),
      "-ca-crt",
      caCrt.toString,
      "-ca-key",
      caKey.toString,
      "-out-crt",
      outCrt.toString
    )

    val unsafeRoutesCommand = unsafeNetworks
      .filter(_.nonEmpty)
      .map(routes => Seq("-unsafe-routes", routes.mkString(",")))
      .getOrElse(Seq.empty)

    baseCommand ++ unsafeRoutesCommand
  }

  def signHostKey(
      caCrt: Path,
      caKey: Path,
      pubKey: Path,
      hostConfig: HostConfig,
      outputDir: Path
  ): IO[Unit] = {
    val hostName = pubKey.fileName.toString.stripSuffix(".pub")
    val certsDir = outputDir / "certs"
    val certFile = certsDir / s"$hostName.crt"

    val createLinks = hostConfig.networks.traverse_ { network =>
      val ipWithoutCidr = network.split('/').head
      val linkFile = certsDir / s"$ipWithoutCidr.crt"
      Files[IO].createSymbolicLink(linkFile, Path(certFile.fileName.toString))
    }

    for {
      _ <- Files[IO].createDirectories(certsDir)
      _ <- IO {
        val command = sign(
          hostConfig.name,
          pubKey,
          hostConfig.networks,
          hostConfig.groups,
          caCrt,
          caKey,
          certFile,
          hostConfig.unsafeNetworks
        )
        val result = command.!!
        println(s"Signing result for $hostName: $result")
      }
      _ <- createLinks
    } yield ()
  }
}
