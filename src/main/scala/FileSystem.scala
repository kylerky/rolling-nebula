package com.nebula.rolling

import cats.effect.IO
import fs2.io.file.{Files, Path}
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import fs2.Stream

object FileSystem {
  private val pubKeyExtension = ".pub"

  def createOutputDirectory(baseDir: Path): IO[Path] = for {
    today <- IO(
      LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    )
    outputDir = baseDir.resolve(s"config_$today")
    _ <- Files[IO].createDirectories(outputDir)
    _ <- Files[IO].createDirectories(outputDir / "certs")
  } yield outputDir

  def getPublicKeyFiles(pubDir: Path): Stream[IO, Path] = {
    Files[IO]
      .walk(pubDir)
      .filter(p => p.toString.endsWith(pubKeyExtension))
      .evalFilter(p => Files[IO].isDirectory(p).map(!_))
  }
}
