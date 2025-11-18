package com.teecertlabs.nebula.rolling

import cats.effect.IO
import fs2.io.file.{Files, Path}
import java.time.format.DateTimeFormatter
import fs2.Stream
import java.time.ZonedDateTime
import java.time.ZoneOffset

trait FileSystem {
  def read(path: String): IO[String]
  def list(path: Path): Stream[IO, Path]
  def exists(path: Path): IO[Boolean]
  def validatePath(path: Path, basePath: Path): IO[Unit]
}

object FileSystem {
  private val pubKeyExtension = ".pub"

  def getTimestamp: IO[String] = IO(
    ZonedDateTime
      .now(ZoneOffset.UTC)
      .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'"))
  )

  def getOutputDirPath(baseDir: Path, timestamp: String): Path =
    baseDir.resolve(s"config_$timestamp")

  def createTempDir(baseDir: Path, name: String): IO[Path] = {
    val tempDir = baseDir.resolve(name)
    for {
      exists <- Files[IO].exists(tempDir)
      _ <- if (exists) Files[IO].deleteRecursively(tempDir) else IO.unit
      _ <- Files[IO].createDirectories(tempDir)
    } yield tempDir
  }

  def renameDir(from: Path, to: Path): IO[Unit] =
    Files[IO].move(from, to)

  def getPublicKeyFiles(pubDir: Path): Stream[IO, Path] = {
    Files[IO]
      .walk(pubDir)
      .filter(p => p.fileName.toString.endsWith(pubKeyExtension))
      .evalFilter(p => Files[IO].isDirectory(p).map(!_))
  }
}

class DefaultFileSystem extends FileSystem {
  def read(path: String): IO[String] =
    Files[IO].readAll(Path(path)).through(fs2.text.utf8.decode).compile.string

  def list(path: Path): Stream[IO, Path] =
    Files[IO].list(path)

  def exists(path: Path): IO[Boolean] =
    Files[IO].exists(path)

  def validatePath(path: Path, basePath: Path): IO[Unit] =
    for {
      realBasePath <- IO.blocking(basePath.toNioPath.toRealPath())
      realPath <- IO.blocking(path.toNioPath.toRealPath())
      _ <-
        if (realPath.startsWith(realBasePath)) IO.unit
        else
          IO.raiseError(
            new IllegalArgumentException("Path traversal attempt detected")
          )
    } yield ()
}
