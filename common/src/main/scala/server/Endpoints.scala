package com.teecertlabs.nebula.rolling.server

import sttp.tapir._
import java.net.InetSocketAddress
import com.teecertlabs.nebula.rolling.ConfigServerConfig
import com.teecertlabs.nebula.rolling.server.Auth
import com.teecertlabs.nebula.rolling.server.HttpError

object Endpoints {

  // Base endpoint definition
  private val baseEndpoint = endpoint.errorOut(HttpError.endpointOutput)

  // /config/lab_server endpoint
  val labServerEndpoint
      : PublicEndpoint[Option[InetSocketAddress], HttpError, String, Any] =
    baseEndpoint.get
      .in("config" / "lab_server")
      .in(extractFromRequest(_.connectionInfo.remote))
      .out(stringBody)
      .description(
        "Serves Nebula configuration for lab servers with specific firewall rules."
      )

  // /config/default endpoint
  val defaultEndpoint
      : PublicEndpoint[Option[InetSocketAddress], HttpError, String, Any] =
    baseEndpoint.get
      .in("config" / "default")
      .in(extractFromRequest(_.connectionInfo.remote))
      .out(stringBody)
      .description(
        "Serves default Nebula configuration with standard firewall rules."
      )

  // Privileged config endpoint
  val privilegedConfigEndpoint: PublicEndpoint[
    (String, Option[InetSocketAddress]),
    HttpError,
    String,
    Any
  ] =
    baseEndpoint.get
      .in("privileged" / "config" / path[String]("hostname"))
      .in(extractFromRequest(_.connectionInfo.remote))
      .out(stringBody)
      .description(
        "Serves host-specific Nebula configuration for privileged access."
      )
}
