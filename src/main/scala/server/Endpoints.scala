package com.nebula.rolling.server

import com.softwaremill.sttp.tapir._
import org.http4s.server.middleware.RequestLogger.RemoteAddress

object Endpoints {

  // Base endpoint definition
  private val baseEndpoint = endpoint.errorOut(stringBody)

  // /config/lab_server endpoint
  val labServerEndpoint: PublicEndpoint[Option[RemoteAddress], String, String, Any] =
    baseEndpoint.get
      .in("config" / "lab_server")
      .in(extractFromRequest(_.remote))
      .out(stringBody)
      .description("Serves Nebula configuration for lab servers with specific firewall rules.")

  // /config/default endpoint
  val defaultEndpoint: PublicEndpoint[Option[RemoteAddress], String, String, Any] =
    baseEndpoint.get
      .in("config" / "default")
      .in(extractFromRequest(_.remote))
      .out(stringBody)
      .description("Serves default Nebula configuration with standard firewall rules.")
}
