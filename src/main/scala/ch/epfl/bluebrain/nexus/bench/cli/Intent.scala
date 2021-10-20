package ch.epfl.bluebrain.nexus.bench.cli

import cats.data.NonEmptyList
import com.comcast.ip4s.IpAddress
import org.http4s.headers.Authorization
import org.http4s.{AuthScheme, Credentials, Uri}

import scala.concurrent.duration.FiniteDuration
import scala.deriving.Mirror

enum Intent:
  case PrepareTestData(
      endpoints: NonEmptyList[Uri],
      token: Option[Authorization],
      organization: String
  )
  case Inject(
      endpoint: NonEmptyList[Uri],
      token: Option[Authorization],
      organization: String,
      concurrency: Int,
      startIdx: Int,
      resourceCount: Int,
      projectCount: Int
  )
  case Test(
      endpoints: NonEmptyList[Uri],
      token: Option[Authorization],
      users: Int,
      organization: String,
      project: String,
      test: TestName,
      maxResourceIdx: Int,
      duration: FiniteDuration
  )
end Intent
