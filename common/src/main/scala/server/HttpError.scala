package com.teecertlabs.nebula.rolling.server

import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.model.StatusCode

import io.circe.generic.auto._

sealed trait HttpError

object HttpError {
  given Schema[HttpError] = Schema.derived
  implicit val encoder: io.circe.Encoder[HttpError] =
    io.circe.Encoder.AsObject.derived[HttpError]

  case class NotFound(message: String = "Not Found") extends HttpError
  case class Unauthorized(message: String = "Unauthorized") extends HttpError
  case class InternalServerError(message: String = "Internal Server Error")
      extends HttpError

  val endpointOutput: EndpointOutput[HttpError] =
    oneOf[HttpError](
      oneOfVariant(
        StatusCode.NotFound,
        jsonBody[NotFound].description("Not Found")
      ),
      oneOfVariant(
        StatusCode.Forbidden,
        jsonBody[Unauthorized].description("Unauthorized")
      ),
      oneOfDefaultVariant(
        statusCode(StatusCode.InternalServerError).and(
          jsonBody[InternalServerError].description("Internal Server Error")
        )
      )
    )
}
