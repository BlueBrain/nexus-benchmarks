package ch.epfl.bluebrain.nexus.bench.util

import cats.effect.*
import cats.data.NonEmptyList
import ch.epfl.bluebrain.nexus.bench.Err
import ch.epfl.bluebrain.nexus.bench.util.Api.accept
import io.circe.*
import org.http4s.Method.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.client.Client
import org.http4s.client.dsl.io.*
import org.http4s.headers.{Accept, Authorization}
import org.http4s.{AuthScheme, Credentials, EntityDecoder, MediaType, Uri}

class Projects(client: Client[IO], endpoints: NonEmptyList[Uri], auth: Option[Authorization]):

  def exists(org: String, proj: String): IO[Boolean] =
    val uri = endpoints.head / "projects" / org / proj
    val req = auth match
      case Some(auth) => GET(uri, auth, Api.accept)
      case None       => GET(uri, Api.accept)
    client.run(req).use { resp =>
      if (resp.status.isSuccess) IO.pure(true)
      else if (resp.status.code == 404) IO.pure(false)
      else Api.genericUnexpectedResponseHandler(resp)
    }

  def create(org: String, proj: String): IO[Unit] =
    val uri  = endpoints.head / "projects" / org / proj
    val body = Json.obj()
    val req  = auth match
      case Some(auth) => POST(body, uri, auth, Api.accept)
      case None       => POST(body, uri, Api.accept)
    client.run(req).use { resp =>
      if (resp.status.code == 201) IO.unit
      else Api.genericUnexpectedResponseHandler(resp)
    }

  def ensureExists(org: String, proj: String): IO[Unit] =
    exists(org, proj).ifM(IO.unit, create(org, proj))
