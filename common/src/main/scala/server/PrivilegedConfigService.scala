package com.teecertlabs.nebula.rolling.server

import cats.effect.IO
import com.teecertlabs.nebula.rolling.ConfigServerConfig
import java.net.InetSocketAddress
import java.net.InetAddress

class PrivilegedConfigService(
    config: ConfigServerConfig,
    templateService: TemplateService
) {

  def getConfig(
      remoteAddress: Option[InetSocketAddress],
      ipFromPath: InetAddress
  ): IO[Either[HttpError, String]] = {
    if (Auth.isPrivilegedIp(config)(remoteAddress)) {
      val ipSocketAddress = new InetSocketAddress(
        ipFromPath,
        0
      ) // Port doesn't matter for IP extraction
      templateService.getConfig(
        "default",
        Some(ipSocketAddress),
        None
      ) // Assuming 'default' firewall type and no limit
    } else {
      IO.pure(Left(HttpError.Unauthorized("Forbidden")))
    }
  }
}
