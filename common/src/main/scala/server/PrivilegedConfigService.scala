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
      allowInboundGroups: Option[List[String]],
      limit: Option[Int],
      hostCertIndex: Option[Int],
      ipFromPath: InetAddress,
      templateName: String
  ): IO[Either[HttpError, String]] = {
    if (Auth.isPrivilegedIp(config)(remoteAddress)) {
      val ipSocketAddress = new InetSocketAddress(
        ipFromPath,
        0
      ) // Port doesn't matter for IP extraction
      templateService.getConfig(
        templateName,
        Some(ipSocketAddress),
        limit,
        hostCertIndex,
        allowInboundGroups
      )
    } else {
      IO.pure(Left(HttpError.Unauthorized("Forbidden")))
    }
  }
}
