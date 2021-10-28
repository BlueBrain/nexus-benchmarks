package ch.epfl.bluebrain.nexus.bench

import cats.effect.*
import ch.epfl.bluebrain.nexus.bench.cli.{Cli, Intent}

object Main extends IOApp:

  override def run(args: List[String]): IO[ExitCode] =
    Terminal()
      .use { terminal =>
        val io: IO[ExitCode] = Cli.command.parse(args) match
          case Left(help)    => Cli.printHelp(terminal, help).as(ExitCode.Success)
          case Right(intent) => evaluate(intent)

        io.handleErrorWith {
          case e: Err => e.println(terminal).as(ExitCode.Error)
          case th     => Err.UnknownErr(th).println(terminal).as(ExitCode.Error)
        }
      }

  private def evaluate(intent: Intent): IO[ExitCode] =
    intent match
      case value: Intent.Inject          => Inject(value)
      case value: Intent.PrepareTestData => PrepareTestData(value)
      case value: Intent.Test            => Test(value)(using runtime)
