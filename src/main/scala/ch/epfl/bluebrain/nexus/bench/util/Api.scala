package ch.epfl.bluebrain.nexus.bench.util

import cats.effect.*
import ch.epfl.bluebrain.nexus.bench.Err
import io.circe.*
import org.http4s.Method.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.client.Client
import org.http4s.client.dsl.io.*
import org.http4s.headers.{Accept, Authorization}
import org.http4s.{AuthScheme, Credentials, EntityDecoder, MediaType, Uri, Response}

import java.net.http.HttpResponse

class Api(client: Client[IO], endpoint: Uri, auth: Option[Authorization]):

  val orgs      = Orgs(client, endpoint, auth)
  val projects  = Projects(client, endpoint, auth)
  val resources = Resources(client, endpoint, auth)
  val schemas   = Schemas(client, endpoint, auth)

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