package com.teecertlabs.nebula.rolling

import cats.effect.IO
import io.circe.generic.auto._
import io.circe.config.parser
import com.typesafe.config.{Config, ConfigFactory}
import fs2.io.file.Path
import java.io.File

case class HostConfig(name: String, ip: String, groups: List[String])

// Cert Roller Configuration
case class CertRollerConfig(
    caName: String,
    pubDir: String,
    hosts: Map[String, HostConfig]
)

// Config Server Configuration
case class ConfigServerConfig(
    host: String,
    port: Int,
    templatePath: String,
    pkiKeyPath: String,
    configDir: String,
    labServerInboundGroups: List[String]
)

object ConfigLoader {
  def loadCertRollerConfig(
      config: Config = ConfigFactory.load()
  ): IO[CertRollerConfig] = {
    parser.decodeF[IO, CertRollerConfig](config.getConfig("cert-roller"))
  }

  def loadConfigServerConfig(
      configFile: Option[File] = None
  ): IO[ConfigServerConfig] = {
    val config = configFile match {
      case Some(file) =>
        ConfigFactory.parseFile(file).withFallback(ConfigFactory.load())
      case None => ConfigFactory.load()
    }
    parser.decodeF[IO, ConfigServerConfig](config.getConfig("config-server"))
  }
}
