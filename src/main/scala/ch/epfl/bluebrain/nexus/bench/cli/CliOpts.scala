package ch.epfl.bluebrain.nexus.bench.cli

import cats.implicits._
import ch.epfl.bluebrain.nexus.bench.BenchConfig.{NoToken, Token, TokenConfig}
import com.monovore.decline._
import com.typesafe.config.ConfigValueFactory
import org.http4s.Uri
import pureconfig.BasicReaders

import scala.concurrent.duration.FiniteDuration

object CliOpts {

  val token: Opts[TokenConfig] = Opts
    .option[String](
      long = "token",
      help = "The token to use when interacting with the Nexus API",
      short = "t",
      metavar = "token"
    )
    .map(_.trim)
    .validate("Token must be a non empty string") { _.length > 0 }
    .map(str => Token(str))

  val noToken: Opts[TokenConfig] = Opts
    .flag(
      long = "no-token",
      help = "Unset a previously set token",
      short = "n"
    )
    .map(_ => NoToken)

  val endpoint: Opts[Uri] = Opts
    .option[String](
      long = "endpoint",
      help = "The base address of the Nexus API",
      short = "e",
      metavar = "endpoint"
    )
    .mapValidated { (str: String) =>
      Uri
        .fromString(str)
        .leftMap(_ => s"Invalid Uri: '$str'")
        .ensure(s"Invalid Uri: '$str'")(uri => uri.scheme.isDefined)
        .toValidatedNel
    }

  val org: Opts[String] = Opts
    .option[String](
      long = "org",
      help = "The organization to be used for loading data and running tests",
      short = "o",
      metavar = "org"
    )
    .validate("Invalid organization label")(_.matches("[a-zA-Z0-9]{1,16}"))

  val duration: Opts[FiniteDuration] = Opts
    .option[String](
      long = "test-duration",
      help = "The test execution duration",
      short = "d",
      metavar = "test-duration"
    )
    .mapValidated { (str: String) =>
      BasicReaders.finiteDurationConfigReader
        .from(ConfigValueFactory.fromAnyRef(str))
        .leftMap(_ => s"Invalid finite duration value '$str', format: '<long><unit>'")
        .toValidatedNel
    }

  val users: Opts[Int] = Opts
    .option[Int](
      long = "users",
      help = "The number of logical threads to be used in tests",
      short = "u",
      metavar = "users"
    )
    .validate("The users value must be in the interval [1, 200]")(u => u > 0 && u <= 200)

  val project: Opts[String] = Opts
    .option[String](
      long = "project",
      help = "The project to be used for running tests",
      short = "p",
      metavar = "project"
    )
    .validate("Invalid project label")(_.matches("[a-zA-Z0-9]{1,16}"))

  val startIdx: Opts[Int] = Opts
    .option[Int](
      long = "start-index",
      help = "The resource index to be used as a starting point; the load op will continue from this index.",
      short = "s",
      metavar = "1 to resource count"
    )
    .validate(s"Start index must be a strictly positive integer")(_ > 0)

}
