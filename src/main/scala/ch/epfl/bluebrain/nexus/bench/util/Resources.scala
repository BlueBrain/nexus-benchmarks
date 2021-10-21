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

import scala.util.Random

class Resources(client: Client[IO], endpoints: NonEmptyList[Uri], auth: Option[Authorization]):

  private val random = Random()
  private val endpointsVector = endpoints.toList.toVector
  private val endpointsSize = endpointsVector.size

  def oneEndpoint: Uri =
    endpointsVector(random.nextInt(endpointsSize))

  def exists(org: String, proj: String, id: Uri): IO[Boolean] =
    val uri = oneEndpoint / "resources" / org / proj / "_" / id.renderString
    val req = auth match
      case Some(auth) => GET(uri, auth, Api.accept)
      case None       => GET(uri, Api.accept)
    client.run(req).use { resp =>
      if (resp.status.isSuccess) IO.pure(true)
      else if (resp.status.code == 404) IO.pure(false)
      else Api.genericUnexpectedResponseHandler(resp)
    }

  def create(org: String, proj: String, id: Uri, body: Json): IO[Unit] =
    val uri = oneEndpoint / "resources" / org / proj / "_" / id.renderString
    val req = auth match
      case Some(auth) => PUT(body, uri, auth, Api.accept)
      case None       => PUT(body, uri, Api.accept)
    client.run(req).use { resp =>
      if (resp.status.code == 201 || resp.status.code == 409) IO.unit
      else Api.genericUnexpectedResponseHandler(resp)
    }

  def ensureExists(org: String, proj: String, id: Uri, body: Json): IO[Unit] =
    exists(org, proj, id).ifM(IO.unit, create(org, proj, id, body))
