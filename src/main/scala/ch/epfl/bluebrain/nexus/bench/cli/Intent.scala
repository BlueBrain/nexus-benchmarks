package ch.epfl.bluebrain.nexus.bench.cli

import cats.data.NonEmptyList
import com.comcast.ip4s.IpAddress
import org.http4s.headers.Authorization
import org.http4s.{AuthScheme, Credentials, Uri}

import scala.concurrent.duration.FiniteDuration
import scala.deriving.Mirror

enum Intent:
  case PrepareTestData(
      endpoint: Uri,
      token: Option[Authorization],
      organization: String
  )
  case Inject(
      endpoint: Uri,
      token: Option[Authorization],
      organization: String,
      concurrency: Int,
      startIdx: Int,
      resourceCount: Int,
      projectCount: Int,
      ipAddresses: Option[NonEmptyList[IpAddress]]
  )
  case Test(
      endpoint: Uri,
      token: Option[Authorization],
      users: Int,
      organization: String,
      project: String,
      test: TestName,
      maxResourceIdx: Int,
      duration: FiniteDuration,
      ipAddresses: Option[NonEmptyList[IpAddress]]
  )
end Intent
