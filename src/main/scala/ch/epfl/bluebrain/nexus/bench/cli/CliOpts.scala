package ch.epfl.bluebrain.nexus.bench.cli

import cats.data.{NonEmptyList, Validated}
import com.comcast.ip4s.IpAddress
import com.monovore.decline.*
import org.http4s.headers.Authorization
import org.http4s.{AuthScheme, Credentials, Uri}

import scala.concurrent.duration.FiniteDuration

trait CliOpts extends CliArgs:

  val endpoint: Opts[Uri] = Opts
    .option[Uri](
      long = "endpoint",
      help = "The base address of the Nexus API",
      short = "e"
    )

  val token: Opts[Authorization] = Opts
    .option[Authorization](
      long = "token",
      help = "The token to use when interacting with the Nexus API",
      short = "t"
    )

  val org: Opts[String] = Opts
    .option[String](
      long = "org",
      help = "The organization to be used for loading data and running tests",
      short = "o",
      metavar = "org"
    )
    .validate("Invalid organization label")(_.matches("[a-zA-Z0-9]{1,16}"))

  val proj: Opts[String] = Opts
    .option[String](
      long = "proj",
      help = "The project to be used for running tests",
      short = "p",
      metavar = "project"
    )
    .validate("Invalid project label")(_.matches("[a-zA-Z0-9]{1,16}"))

  val concurrency: Opts[Int] = Opts
    .option[Int](
      long = "concurrency",
      help = "The level of concurrency to use when interacting with the Nexus API",
      short = "c",
      metavar = "concurrency"
    )

  val ipAddresses: Opts[NonEmptyList[IpAddress]] = Opts
    .options[IpAddress](
      long = "ip-address",
      help = "IP address to be used when connecting to the Nexus API instead of the provided endpoint hostname",
      short = "i"
    )

  val startIndex: Opts[Int] = Opts
    .option[Int](
      long = "start-index",
      help = "The resource index to start with (in case a previous execution as aborted)",
      short = "s",
      metavar = "index"
    )
    .validate("The start-index value must be a strict positive integer.")(_ > 0)

  val resourceCount: Opts[Int] = Opts
    .option[Int](
      long = "total",
      help = "Total number of resources to inject across all projects",
      metavar = "resource count"
    )
    .validate("The total value must be a strict positive integer.")(_ > 0)

  val projectCount: Opts[Int] = Opts
    .option[Int](
      long = "projects",
      help = "Total number of projects to be created",
      metavar = "project count"
    )
    .validate("The projects value must be a strict positive integer.")(_ > 0)

  val test: Opts[TestName] = Opts
    .option[String](
      long = "test",
      help = s"The name of the test to execute, one of: ${TestName.values.map(_.value).mkString("'", "', '", "'")}",
      metavar = "test name"
    )
    .mapValidated { str =>
      TestName.values.find(_.value == str) match
        case Some(tn) => Validated.validNel(tn)
        case None     =>
          Validated.invalidNel(
            s"The name of the test to execute must be one of: ${TestName.values.map(_.value).mkString("'", "', '", "'")}"
          )
    }

  val users: Opts[Int] = Opts
    .option[Int](
      long = "users",
      help = "The gatling users configuration, essentially the concurrency level for the test",
      metavar = "users"
    )

  val maxResourceIdx: Opts[Int] = Opts
    .option[Int](
      long = "max-resource-index",
      help = "The maximum resource index to be used for read tests",
      metavar = "max index"
    )
    .validate("The max-resource-index value must be a strict positive integer.")(_ > 0)

  val duration: Opts[FiniteDuration] = Opts
    .option[FiniteDuration](
      long = "duration",
      help = "The gatling test duration",
      metavar = "duration"
    )

end CliOpts

object CliOpts extends CliOpts
