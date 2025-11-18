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
      .map(_.map(_.split(',').toList.filter(_.nonEmpty)))(
        _.map(_.mkString(","))
      )

  // Base endpoint definition with common inputs
  private val baseEndpoint = endpoint
    .errorOut(HttpError.endpointOutput)
    .in(extractFromRequest(_.connectionInfo.remote))
    .in(allowInboundGroupsInput)
    .in(query[Option[Int]]("limit"))

  // /config/{templateName} endpoint
  val unifiedTemplateEndpoint: PublicEndpoint[
    (Option[InetSocketAddress], Option[List[String]], Option[Int], String),
    HttpError,
    String,
    Any
  ] =
    baseEndpoint.get
      .in("config" / path[String]("templateName"))
      .out(stringBody)
      .description("Serves a Nebula configuration from a named template.")

  // Privileged config endpoint
  val privilegedConfigEndpoint: PublicEndpoint[
    (
        Option[InetSocketAddress],
        Option[List[String]],
        Option[Int],
        java.net.InetAddress,
        String
    ),
    HttpError,
    String,
    Any
  ] =
    baseEndpoint.get
      .in(
        "privileged" / "config" / path[java.net.InetAddress]("ip") / path[String](
          "templateName"
        )
      )
      .out(stringBody)
      .description(
        "Serves host-specific Nebula configuration for privileged access."
      )
}
