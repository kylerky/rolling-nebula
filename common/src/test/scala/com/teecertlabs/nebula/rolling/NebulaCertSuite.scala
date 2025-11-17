package com.teecertlabs.nebula.rolling

import munit.FunSuite
import fs2.io.file.Path

class NebulaCertSuite extends FunSuite {

  test("sign command should include -unsafe-routes when provided") {
    val unsafeNetworks = Some(List("10.0.0.0/8", "192.168.1.0/24"))
    val command = NebulaCert.sign(
      "test-host",
      Path("/keys/test-host.pub"),
      "172.16.0.1/16",
      List("group1", "group2"),
      Path("/ca/ca.crt"),
      Path("/ca/ca.key"),
      Path("/out/test-host.crt"),
      unsafeNetworks
    )

    val expected =
      "nebula-cert sign -name test-host -in-pub /keys/test-host.pub -ip 172.16.0.1/16 -groups group1,group2 -ca-crt /ca/ca.crt -ca-key /ca/ca.key -out-crt /out/test-host.crt -unsafe-routes 10.0.0.0/8,192.168.1.0/24"
    assertEquals(command.mkString(" "), expected)
  }

  test("sign command should not include -unsafe-routes when not provided") {
    val command = NebulaCert.sign(
      "test-host",
      Path("/keys/test-host.pub"),
      "172.16.0.1/16",
      List("group1", "group2"),
      Path("/ca/ca.crt"),
      Path("/ca/ca.key"),
      Path("/out/test-host.crt"),
      None
    )

    val expected =
      "nebula-cert sign -name test-host -in-pub /keys/test-host.pub -ip 172.16.0.1/16 -groups group1,group2 -ca-crt /ca/ca.crt -ca-key /ca/ca.key -out-crt /out/test-host.crt"
    assertEquals(command.mkString(" "), expected)
  }

  test("sign command should not include -unsafe-routes when list is empty") {
    val command = NebulaCert.sign(
      "test-host",
      Path("/keys/test-host.pub"),
      "172.16.0.1/16",
      List("group1", "group2"),
      Path("/ca/ca.crt"),
      Path("/ca/ca.key"),
      Path("/out/test-host.crt"),
      Some(List.empty)
    )

    val expected =
      "nebula-cert sign -name test-host -in-pub /keys/test-host.pub -ip 172.16.0.1/16 -groups group1,group2 -ca-crt /ca/ca.crt -ca-key /ca/ca.key -out-crt /out/test-host.crt"
    assertEquals(command.mkString(" "), expected)
  }
}
