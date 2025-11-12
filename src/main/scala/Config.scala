package com.nebula.rolling

import cats.effect.IO
import io.circe.generic.auto._
import io.circe.config.parser

case class HostConfig(name: String, ip: String, groups: List[String])
case class AppConfig(caName: String, hosts: Map[String, HostConfig])

object ConfigLoader {
  def load(): IO[AppConfig] = {
    parser.decodeF[IO, AppConfig]()
  }
}
