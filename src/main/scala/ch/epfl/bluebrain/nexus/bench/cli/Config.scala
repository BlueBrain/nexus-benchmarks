package ch.epfl.bluebrain.nexus.bench.cli

import java.nio.file.{Path, Paths}

import cats.effect.{Blocker, ContextShift, ExitCode, Sync}
import ch.epfl.bluebrain.nexus.bench.BenchError
import ch.epfl.bluebrain.nexus.bench.BenchError.IllegalPath
import com.monovore.decline.Opts
import cats.implicits._
import fs2.io

import scala.util.Try
import scala.util.control.NonFatal

object Config {

  def apply[F[_]: Sync: ContextShift](blocker: Blocker): Opts[F[ExitCode]] =
    Opts.subcommand("config", "Read or write the tool configuration.") {
      reset[F](blocker) orElse show[F]
    }

  def reset[F[_]: ContextShift: Sync](blocker: Blocker): Opts[F[ExitCode]] =
    Opts.subcommand("reset", "Resets configuration to default.") {
      configFile[F]
        .flatMap { path =>
          io.file.deleteIfExists(blocker, path).as(ExitCode.Success)
        }
        .pure[Opts]
    }

  def show[F[_]](implicit F: Sync[F]): Opts[F[ExitCode]] =
    Opts.subcommand("show", "Shows the current configuration.") {
      F.delay(println("The config")).as(ExitCode.Success).pure[Opts]
    }


  private def configDir[F[_]](implicit F: Sync[F]): F[Path] = {
    def homeStringFromProps =
      F.fromOption(sys.props.get("user.home"), BenchError.UserHomeNotDefined)
    def homePath(path: String) =
      F.fromTry(Try(Paths.get(path))).recoverWith {
        case NonFatal(_) => F.raiseError(IllegalPath(path))
      }
    for {
      pathString <- homeStringFromProps
      path       <- homePath(pathString)
    } yield path.resolve(".nxb")
  }

  private def configFile[F[_]: Sync]: F[Path] =
    configDir[F].map(_.resolve("config"))
}
