package com.nebula.rolling

import io.circe.generic.auto._
import io.circe.config.parser
import scala.util.Try

case class HostConfig(name: String, ip: String, groups: List[String])
case class AppConfig(hosts: Map[String, HostConfig])

object ConfigLoader {
  def load(): Try[AppConfig] = {
    parser.decode[AppConfig]().toTry
  }
}
