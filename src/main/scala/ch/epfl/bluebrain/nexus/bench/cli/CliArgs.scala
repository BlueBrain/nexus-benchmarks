package ch.epfl.bluebrain.nexus.bench.cli

import cats.data.*
import cats.implicits.*
import com.monovore.decline.*
import org.http4s.headers.Authorization
import com.comcast.ip4s.IpAddress
import org.http4s.{AuthScheme, Credentials, Uri}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.Duration
import scala.concurrent.duration.Duration.Infinite
import scala.util.{Failure, Success, Try}

trait CliArgs:

  given Argument[Uri] =
    Argument.from("uri") { str =>
      Uri
        .fromString(str)
        .leftMap(_ => s"Invalid Uri: '$str'")
        .ensure(s"Invalid Uri: '$str'")(uri => uri.scheme.isDefined)
        .toValidatedNel
    }

  given Argument[IpAddress] =
    Argument.from("ip address") { str =>
      IpAddress
        .fromString(str)
        .toRight(s"Invalid IpAddress: '$str'")
        .toValidatedNel
    }

  given Argument[Authorization] =
    Argument.from("token") { str =>
      Either
        .cond(str.length > 0, str.trim, "Token must be a non empty string")
        .toValidatedNel
        .map(value => Authorization(Credentials.Token(AuthScheme.Bearer, value)))
    }

  given Argument[FiniteDuration] =
    Argument.from("duration") { str =>
      val either: Either[String, FiniteDuration] =
        Try(Duration.create(str)) match
          case Success(fd: FiniteDuration) => Right(fd)
          case Success(_: Infinite)        => Left("The duration must be finite")
          case Failure(_)                  => Left("Invalid duration, must be '<number><unit>'")
      either.toValidatedNel
    }

end CliArgs

object CliArgs extends CliArgs
