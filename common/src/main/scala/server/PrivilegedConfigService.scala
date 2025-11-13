package com.teecertlabs.nebula.rolling.server

import cats.effect.IO
import com.teecertlabs.nebula.rolling.ConfigServerConfig
import com.teecertlabs.nebula.rolling.FileSystem
import java.net.InetSocketAddress

class PrivilegedConfigService(
    config: ConfigServerConfig,
    fileSystem: FileSystem
) {

  def getConfig(
      remoteAddress: Option[InetSocketAddress],
      hostname: String
  ): IO[Either[HttpError, String]] = {
    if (Auth.isPrivilegedIp(config)(remoteAddress)) {
      val filePath = s"${config.configDir}/$hostname.yaml"
      fileSystem
        .read(filePath)
        .map[Either[HttpError, String]](Right(_))
        .handleErrorWith {
          case _: java.nio.file.NoSuchFileException =>
            IO.pure(
              Left(
                HttpError
                  .NotFound(s"Configuration for host '$hostname' not found.")
              )
            )
          case e: Throwable => IO.raiseError(e)
        }
    } else {
      IO.pure(Left(HttpError.Unauthorized("Forbidden")))
    }
  }
}
