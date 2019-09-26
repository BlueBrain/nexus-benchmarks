package ch.epfl.bluebrain.nexus.bench.cli

import cats.implicits._
import com.monovore.decline._
import org.http4s.Uri

object CliOpts {

  sealed trait TokenConfig
  final case class Token(value: String) extends TokenConfig
  final case object NoToken             extends TokenConfig

  final case class EndpointConfig(value: Uri)

  final case class EnvConfig(token: TokenConfig, endpoint: EndpointConfig)

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
      short = "nt"
    )
    .map(_ => NoToken)

  val endpoint: Opts[EndpointConfig] = Opts
    .option[String](
      long = "endpoint",
      help = "The base address of the Nexus API",
      short = "e",
      metavar = "endpoint"
    )
    .mapValidated { str =>
      Uri.fromString(str).map(EndpointConfig).leftMap(_ => s"Invalid Uri: '$str'").toValidatedNel
    }

}
