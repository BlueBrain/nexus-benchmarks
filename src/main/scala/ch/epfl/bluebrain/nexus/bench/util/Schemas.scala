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

class Schemas(client: Client[IO], endpoints: NonEmptyList[Uri], auth: Option[Authorization]):

  def exists(org: String, proj: String, id: Uri): IO[Boolean] =
    val uri = endpoints.head / "schemas" / org / proj / id.renderString
    val req = auth match
      case Some(auth) => GET(uri, auth, Api.accept)
      case None       => GET(uri, Api.accept)
    client.run(req).use { resp =>
      if (resp.status.isSuccess) IO.pure(true)
      else if (resp.status.code == 404) IO.pure(false)
      else Api.genericUnexpectedResponseHandler(resp)
    }

  def create(org: String, proj: String, id: Uri, body: Json): IO[Unit] =
    val uri = endpoints.head / "schemas" / org / proj / id.renderString
    val req = auth match
      case Some(auth) => PUT(body, uri, auth, Api.accept)
      case None       => PUT(body, uri, Api.accept)
    client.run(req).use { resp =>
      if (resp.status.code == 201) IO.unit
      else Api.genericUnexpectedResponseHandler(resp)
    }

  def ensureExists(org: String, proj: String, id: Uri, body: Json): IO[Unit] =
    exists(org, proj, id).ifM(IO.unit, create(org, proj, id, body))

  def update(org: String, proj: String, id: Uri, rev: Long, body: Json): IO[Unit] =
    val uri = (endpoints.head / "schemas" / org / proj / id.renderString).withQueryParam("rev", rev)
    val req = auth match
      case Some(auth) => PUT(body, uri, auth, Api.accept)
      case None       => PUT(body, uri, Api.accept)
    client.run(req).use { resp =>
      if (resp.status.isSuccess) IO.unit
      else Api.genericUnexpectedResponseHandler(resp)
    }

  def get(org: String, proj: String, id: Uri): IO[Option[Json]] =
    val uri = endpoints.head / "schemas" / org / proj / id.renderString
    val req = auth match
      case Some(auth) => GET(uri, auth, Api.accept)
      case None       => GET(uri, Api.accept)
    client.run(req).use { resp =>
      if (resp.status.isSuccess)
        summon[EntityDecoder[IO, Json]]
          .decode(resp, strict = false)
          .value
          .flatMap {
            case Left(err)    => IO.raiseError(Err.DecodeErr(err.message))
            case Right(value) => IO.pure(Some(value))
          }
      else if (resp.status.code == 404) IO.pure(None)
      else Api.genericUnexpectedResponseHandler(resp)
    }

  def revisionOf(org: String, proj: String, id: Uri): IO[Option[Long]] =
    get(org, proj, id).flatMap {
      case Some(value) =>
        value.hcursor.get[Long]("_rev") match
          case Left(err: DecodingFailure) =>
            val msg = s"Failed to decode response when fetching the revision of schema '$org/$proj/${id.renderString}'"
            IO.raiseError(Err.DecodeErr(msg + System.lineSeparator() + err.getMessage))
          case Right(rev)                 => IO.pure(Some(rev))
      case None        =>
        IO.pure(None)
    }
