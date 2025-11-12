package com.nebula.rolling

import cats.effect.IO
import io.circe.generic.auto._
import io.circe.config.parser
import com.typesafe.config.{Config, ConfigFactory}

case class HostConfig(name: String, ip: String, groups: List[String])

// Cert Roller Configuration
case class CertRollerConfig(caName: String, pubDir: String, hosts: Map[String, HostConfig])

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
  def loadCertRollerConfig(config: Config = ConfigFactory.load()): IO[CertRollerConfig] = {
    parser.decodeF[IO, CertRollerConfig](config.getConfig("cert-roller"))
  }

  def loadConfigServerConfig(config: Config = ConfigFactory.load()): IO[ConfigServerConfig] = {
    parser.decodeF[IO, ConfigServerConfig](config.getConfig("config-server"))
  }
}
