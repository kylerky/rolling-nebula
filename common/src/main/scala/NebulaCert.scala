package com.teecertlabs.nebula.rolling

import cats.effect.IO
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
    val ipWithoutCidr = hostConfig.ip.split('/').head // Remove CIDR suffix
    val linkFile = certsDir / s"$ipWithoutCidr.crt"

    for {
      _ <- Files[IO].createDirectories(certsDir)
      _ <- IO {
        val command = Seq(
          "nebula-cert",
          "sign",
          "-ca-crt",
          caCrt.toString,
          "-ca-key",
          caKey.toString,
          "-in-pub",
          pubKey.toString,
          "-name",
          hostConfig.name,
          "-ip",
          hostConfig.ip,
          "-groups",
          hostConfig.groups.mkString(","),
          "-out-crt",
          certFile.toString
        )
        val result = command.!!
        println(s"Signing result for $hostName: $result")
      }
      _ <- Files[IO].createSymbolicLink(linkFile, Path(certFile.fileName.toString))
    } yield ()
  }
}
