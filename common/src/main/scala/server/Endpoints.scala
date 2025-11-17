package com.teecertlabs.nebula.rolling.server

import sttp.tapir._
import java.net.InetSocketAddress
import com.teecertlabs.nebula.rolling.ConfigServerConfig
import com.teecertlabs.nebula.rolling.server.Auth
import com.teecertlabs.nebula.rolling.server.HttpError
import sttp.tapir.Codec
import sttp.tapir.CodecFormat
import cats.implicits._ // Added for Either.catchNonFatal

object Endpoints {

  implicit val inetAddressCodec
      : Codec[String, java.net.InetAddress, CodecFormat.TextPlain] =
    Codec.string.mapEither { s =>
      Either
        .catchNonFatal(java.net.InetAddress.getByName(s))
        .leftMap(e => s"Invalid IP address: ${e.getMessage}")
    }(_.getHostAddress)

  // Base endpoint definition
  private val baseEndpoint = endpoint.errorOut(HttpError.endpointOutput)

  // /config/lab_server endpoint
  val labServerEndpoint: PublicEndpoint[
    (Option[InetSocketAddress], Option[Int]),
    HttpError,
    String,
    Any
  ] =
    baseEndpoint.get
      .in("config" / "lab_server")
      .in(extractFromRequest(_.connectionInfo.remote))
      .in(query[Option[Int]]("limit"))
      .out(stringBody)
      .description(
        "Serves Nebula configuration for lab servers with specific firewall rules."
      )

  // /config/default endpoint
  val defaultEndpoint: PublicEndpoint[
    (Option[InetSocketAddress], Option[Int]),
    HttpError,
    String,
    Any
  ] =
    baseEndpoint.get
      .in("config" / "default")
      .in(extractFromRequest(_.connectionInfo.remote))
      .in(query[Option[Int]]("limit"))
      .out(stringBody)
      .description(
        "Serves default Nebula configuration with standard firewall rules."
      )

  // Privileged config endpoint
  val privilegedConfigEndpoint: PublicEndpoint[
    (java.net.InetAddress, Option[InetSocketAddress]),
    HttpError,
    String,
    Any
  ] =
    baseEndpoint.get
      .in("privileged" / "config" / path[java.net.InetAddress]("ip"))
      .in(extractFromRequest(_.connectionInfo.remote))
      .out(stringBody)
      .description(
        "Serves host-specific Nebula configuration for privileged access."
      )
}
