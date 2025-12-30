package com.teecertlabs.nebula.rolling

import munit.CatsEffectSuite
import cats.effect.IO
import fs2.io.file.{Files, Path}
import fs2.Stream

class FileSystemSuite extends CatsEffectSuite {

  test("findLatestConfigDirs should return the top N directories sorted by timestamp descending") {
    val baseDir = Path("target/test-fs-latest-n")
    
    val timestamps = (1 to 5).map(i => f"2023-01-0${i}T10-00-00Z").toList
    // timestamps: ...01, ...02, ...03, ...04, ...05
    
    val setup = for {
      _ <- Files[IO].createDirectories(baseDir)
      _ <- Stream.emits(timestamps).evalMap { ts =>
        Files[IO].createDirectory(baseDir / s"config_$ts")
      }.compile.drain
      _ <- Files[IO].createDirectory(baseDir / "other_dir") // Should be ignored
    } yield ()

    val test = for {
      latest <- FileSystem.findLatestConfigDirs(baseDir, 3)
      _ <- IO(assertEquals(latest.map(_.fileName.toString), List(
        s"config_${timestamps(4)}", // 05
        s"config_${timestamps(3)}", // 04
        s"config_${timestamps(2)}"  // 03
      )))
    } yield ()
    
    val cleanup = Files[IO].deleteRecursively(baseDir)
    
    setup *> test.guarantee(cleanup)
  }
  
  test("findLatestConfigDirs should return all directories if n > count") {
     val baseDir = Path("target/test-fs-all")
     val timestamps = List("2023-01-01T10-00-00Z", "2023-01-02T10-00-00Z")
     
     val setup = for {
        _ <- Files[IO].createDirectories(baseDir)
        _ <- Stream.emits(timestamps).evalMap { ts =>
          Files[IO].createDirectory(baseDir / s"config_$ts")
        }.compile.drain
     } yield ()
     
     val test = for {
       latest <- FileSystem.findLatestConfigDirs(baseDir, 10)
       _ <- IO(assertEquals(latest.length, 2))
       _ <- IO(assertEquals(latest.head.fileName.toString, s"config_${timestamps(1)}"))
     } yield ()
     
     val cleanup = Files[IO].deleteRecursively(baseDir)
     
     setup *> test.guarantee(cleanup)
  }
}
