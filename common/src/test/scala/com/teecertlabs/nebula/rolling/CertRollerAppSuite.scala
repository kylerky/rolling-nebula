package com.teecertlabs.nebula.rolling

import munit.CatsEffectSuite
import com.monovore.decline.Command

class CertRollerAppSuite extends CatsEffectSuite {

  test("CertRollerApp CLI should accept 'roll' subcommand with an argument") {
    // Current behavior: 'roll' would be interpreted as base-dir, 'some-dir' would be extra -> fail.
    // Desired behavior: 'roll' is subcommand, 'some-dir' is base-dir arg to roll -> pass.
    
    val command = Command("cert-roller", "header")(CertRollerApp.app)
    val args = List("roll", "some-dir")
    
    command.parse(args) match {
      case Right(_) => () // Success
      case Left(help) => fail(s"Parsing failed: $help")
    }
  }

  test("CertRollerApp CLI should require a subcommand (root command should not run logic)") {
     // Current behavior: parses successfully using default dir.
     // Desired behavior: fails because 'roll' (or 'update') is required.
     
     val command = Command("cert-roller", "header")(CertRollerApp.app)
     val args = List.empty[String]
     
     command.parse(args) match {
       case Right(_) => fail("Should have failed because no subcommand was provided")
       case Left(_) => () // Success (help/error displayed)
     }
  }
}
