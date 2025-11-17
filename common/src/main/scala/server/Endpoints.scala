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

  // Reusable input for the allow_inbound_groups query parameter
  private val allowInboundGroupsInput: EndpointInput[Option[List[String]]] =
    query[Option[String]]("allow_inbound_groups")
      .map(_.map(_.split(',').toList.filter(_.nonEmpty)))(_.map(_.mkString(",")))

  // Base endpoint definition with common inputs
  private val baseEndpoint = endpoint
    .errorOut(HttpError.endpointOutput)
    .in(extractFromRequest(_.connectionInfo.remote))
    .in(allowInboundGroupsInput)
    .in(query[Option[Int]]("limit"))

  // /config/lab_server endpoint
  val labServerEndpoint: PublicEndpoint[
    (Option[InetSocketAddress], Option[List[String]], Option[Int]),
    HttpError,
    String,
    Any
  ] =
    baseEndpoint.get
      .in("config" / "lab_server")
      .out(stringBody)
      .description(
        "Serves Nebula configuration for lab servers with specific firewall rules."
      )

  // /config/default endpoint
  val defaultEndpoint: PublicEndpoint[
    (Option[InetSocketAddress], Option[List[String]], Option[Int]),
    HttpError,
    String,
    Any
  ] =
    baseEndpoint.get
      .in("config" / "default")
      .out(stringBody)
      .description(
        "Serves default Nebula configuration with standard firewall rules."
      )

  // Privileged config endpoint
  val privilegedConfigEndpoint: PublicEndpoint[
    (Option[InetSocketAddress], Option[List[String]], Option[Int], java.net.InetAddress),
    HttpError,
    String,
    Any
  ] =
    baseEndpoint.get
      .in("privileged" / "config" / path[java.net.InetAddress]("ip"))
      .out(stringBody)
      .description(
        "Serves host-specific Nebula configuration for privileged access."
      )
}
