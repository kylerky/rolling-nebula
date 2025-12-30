package com.teecertlabs.nebula.rolling.util

import cats.effect.{IO, ExitCode}
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp
import org.typelevel.log4cats.Logger

abstract class BaseApp(name: String, header: String)
    extends CommandIOApp(name, header) {

  // The logger must be defined by the subclass
  protected given logger: Logger[IO]

  // Subclasses now implement this instead of `main`
  def app: Opts[IO[ExitCode]]

  // The final `main` is sealed here to apply consistent error handling
  final override def main: Opts[IO[ExitCode]] = {
    app.map { logicIO =>
      logicIO.handleErrorWith { err =>
        logger.error(err)(
          s"An unhandled error occurred: ${err.getMessage}"
        ) *> IO(ExitCode.Error)
      }
    }
  }
}
