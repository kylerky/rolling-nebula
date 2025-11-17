package com.teecertlabs.nebula.rolling

import cats.effect.IO
import com.typesafe.config.ConfigFactory
import munit.FunSuite
import io.circe.config.parser
import io.circe.generic.auto._
import cats.effect.unsafe.implicits.global

class ConfigSuite extends FunSuite {
  test("HostConfig should parse unsafeNetworks when the key is present") {
    val configString =
      """
        |certRoller {
        |  caName = "MyNebulaCA"
        |  pubDir = "pubs"
        |  hosts = {
        |    "host1": {
        |      name: "host1.example.com",
        |      networks: ["192.168.1.10/24"],
        |      groups: ["default-group"],
        |      unsafeNetworks = ["192.168.1.0/24", "10.0.0.0/8"]
        |    }
        |  }
        |}
        |""".stripMargin
    val config = ConfigFactory.parseString(configString)
    val decodedConfig = parser.decodeF[IO, CertRollerConfig](config.getConfig("certRoller")).unsafeRunSync()

    val host1Config = decodedConfig.hosts.get("host1")
    assert(host1Config.isDefined, "host1 config should be defined")
    assertEquals(host1Config.get.networks, List("192.168.1.10/24"))
    assertEquals(
      host1Config.get.unsafeNetworks,
      Some(List("192.168.1.0/24", "10.0.0.0/8"))
    )
  }

  test("HostConfig should have no unsafeNetworks when the key is not present") {
    val configString =
      """
        |certRoller {
        |  caName = "MyNebulaCA"
        |  pubDir = "pubs"
        |  hosts = {
        |    "host2": {
        |      name: "host2.example.com",
        |      networks: ["192.168.1.11/24", "10.0.0.1/16"],
        |      groups: ["default-group"]
        |    }
        |  }
        |}
        |""".stripMargin
    val config = ConfigFactory.parseString(configString)
    val decodedConfig = parser.decodeF[IO, CertRollerConfig](config.getConfig("certRoller")).unsafeRunSync()

    val host2Config = decodedConfig.hosts.get("host2")
    assert(host2Config.isDefined, "host2 config should be defined")
    assertEquals(host2Config.get.networks, List("192.168.1.11/24", "10.0.0.1/16"))
    assertEquals(host2Config.get.unsafeNetworks, None)
  }
}
