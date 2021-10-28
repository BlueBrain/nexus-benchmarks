package ch.epfl.bluebrain.nexus.bench.util

import cats.effect.*
import cats.data.NonEmptyList
import ch.epfl.bluebrain.nexus.bench.Err
import io.circe.*
import org.http4s.Method.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.client.Client
import org.http4s.client.dsl.io.*
import org.http4s.headers.{Accept, Authorization}
import org.http4s.{AuthScheme, Credentials, EntityDecoder, MediaType, Response, Uri}

import java.net.http.HttpResponse

class Api(client: Client[IO], endpoints: NonEmptyList[Uri], auth: Option[Authorization]):

  val orgs      = Orgs(client, endpoints, auth)
  val projects  = Projects(client, endpoints, auth)
  val resources = Resources(client, endpoints, auth)
  val schemas   = Schemas(client, endpoints, auth)

object Api:
  val accept: Accept = Accept(MediaType.application.json)

  def genericUnexpectedResponseHandler(resp: Response[IO]): IO[Nothing] =
    summon[EntityDecoder[IO, Json]]
      .decode(resp, strict = false)
      .value
      .flatMap {
        case Left(err)    => IO.raiseError(Err.DecodeErr(err.message))
        case Right(value) => IO.raiseError(Err.UnexpectedResponse(resp.status.code, value.spaces2))
      }
