package com.teecertlabs.nebula.rolling

import cats.effect.IO
import io.circe.generic.auto._
import io.circe.config.parser
import com.typesafe.config.{Config, ConfigFactory}
import fs2.io.file.Path
import java.io.File

case class HostConfig(
    name: String,
    networks: List[String],
    groups: List[String],
    unsafeNetworks: Option[List[String]]
)

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
    templatesDir: String,
    pkiKeyPath: String,
    configDir: String,
    labServerInboundGroups: List[String],
    privilegedIps: List[String],
    numConfigs: Option[Int]
)

object ConfigLoader {
  def loadCertRollerConfig(
      configFile: Option[File] = None
  ): IO[CertRollerConfig] = {
    val config = configFile match {
      case Some(file) =>
        ConfigFactory.parseFile(file).withFallback(ConfigFactory.load())
      case None => ConfigFactory.load()
    }
    parser.decodeF[IO, CertRollerConfig](config.getConfig("certRoller"))
  }

  def loadConfigServerConfig(
      configFile: Option[File] = None
  ): IO[ConfigServerConfig] = {
    val config = configFile match {
      case Some(file) =>
        ConfigFactory.parseFile(file).withFallback(ConfigFactory.load())
      case None => ConfigFactory.load()
    }
    parser.decodeF[IO, ConfigServerConfig](config.getConfig("configServer"))
  }
}
