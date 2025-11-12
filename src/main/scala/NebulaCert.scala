package com.nebula.rolling

import cats.effect.IO
import fs2.io.file.Path
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
  ): IO[Unit] = IO {
    val hostName = pubKey.fileName.toString.stripSuffix(".pub")
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
      (outputDir / "certs" / s"$hostName.crt").toString
    )
    val result = command.!!
    println(s"Signing result for $hostName: $result")
  }
}
