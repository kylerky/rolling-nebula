package com.teecertlabs.nebula.rolling.server

import com.teecertlabs.nebula.rolling.ConfigServerConfig
import sttp.tapir.server.http4s.Http4sServerRequest
import sttp.tapir.model.ConnectionInfo
import java.net.InetSocketAddress

object Auth {

  def isPrivilegedIp(
      config: ConfigServerConfig
  )(remoteAddress: Option[InetSocketAddress]): Boolean = {
    remoteAddress match {
      case Some(addr) =>
        val clientIp = addr.getAddress.getHostAddress
        config.privilegedIps.contains(clientIp)
      case None => false
    }
  }
}
