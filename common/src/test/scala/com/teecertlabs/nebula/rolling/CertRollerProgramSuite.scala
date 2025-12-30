package com.teecertlabs.nebula.rolling

import munit.CatsEffectSuite
import cats.effect.IO
import cats.implicits._
import fs2.io.file.Path
import fs2.Stream
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

class CertRollerProgramSuite extends CatsEffectSuite {
  given Logger[IO] = Slf4jLogger.getLoggerFromName("test")
  
  class MockFileSystem(
      existsMap: Map[String, Boolean] = Map.empty,
      latestDirs: List[Path] = List.empty
  ) extends FileSystem {
    def read(path: String): IO[String] = IO.pure("")
    def list(path: Path): Stream[IO, Path] = Stream.empty
    def exists(path: Path): IO[Boolean] = IO.pure(existsMap.getOrElse(path.toString, false))
    def validatePath(path: Path, basePath: Path): IO[Unit] = IO.unit
    override def createTempDir(baseDir: Path, name: String): IO[Path] = IO.pure(baseDir / name)
    override def renameDir(from: Path, to: Path): IO[Unit] = IO.unit
    override def getPublicKeyFiles(pubDir: Path): Stream[IO, Path] = Stream.empty
    override def getTimestamp: IO[String] = IO.pure("timestamp")
    override def findLatestConfigDirs(baseDir: Path, n: Int): IO[List[Path]] = IO.pure(latestDirs)
  }

  test("update should call signHostKey for all latest config directories") {
    val baseDir = Path("/base")
    val pubDir = Path("/pub")
    val hostname = "host1"
    
    val timestamps = List("ts1", "ts2")
    val latestDirs = timestamps.map(ts => baseDir / s"config_$ts")
    
    // Mock FileSystem state
    val existsMap = Map(
        (pubDir / s"$hostname.pub").toString -> true,
        (latestDirs(0) / "ca.crt").toString -> true,
        (latestDirs(0) / "ca.key").toString -> true,
        (latestDirs(1) / "ca.crt").toString -> true,
        (latestDirs(1) / "ca.key").toString -> true
    )
    
    given FileSystem = new MockFileSystem(existsMap, latestDirs)
    
    val hostConfig = HostConfig(hostname, List("10.0.0.1/24"), List("group1"), None)
    val config = CertRollerConfig("my-ca", pubDir.toString, Map(hostname -> hostConfig), Some(2))
    
    var signedDirs = List.empty[String]
    val mockCertService = new NebulaCertService[IO] {
      def generateCA(baseCaName: String, outputDir: Path): IO[Unit] = IO.unit
      def signHostKey(caCrt: Path, caKey: Path, pubKey: Path, hostConfig: HostConfig, outputDir: Path): IO[Unit] = IO {
        signedDirs = signedDirs :+ outputDir.toString
      }
    }
    
    val program = new CertRollerProgram(mockCertService)
    
    for {
        _ <- program.update(baseDir, config, hostname, None)
        _ <- IO(assertEquals(signedDirs.length, 2))
        _ <- IO(assert(signedDirs.contains(latestDirs(0).toString)))
        _ <- IO(assert(signedDirs.contains(latestDirs(1).toString)))
    } yield ()
  }
}
