package ch.epfl.bluebrain.nexus.bench.cli

import java.nio.file.{Path, Paths}

import cats.effect.{Blocker, ContextShift, ExitCode, Sync}
import cats.implicits._
import ch.epfl.bluebrain.nexus.bench.BenchError.{ConfigurationError, IllegalPath}
import ch.epfl.bluebrain.nexus.bench.{BenchConfig, BenchError}
import com.monovore.decline.Opts
import com.typesafe.config.ConfigRenderOptions
import fs2.io
import pureconfig.{ConfigObjectSource, ConfigSource, ConfigWriter}

import scala.util.Try
import scala.util.control.NonFatal

class Config[F[_]: ContextShift](blocker: Blocker)(implicit F: Sync[F]) {

  def subcommand: Opts[F[ExitCode]] =
    Opts.subcommand("config", "Read or write the tool configuration.") {
      reset orElse show
    }

  def reset: Opts[F[ExitCode]] =
    Opts.subcommand("reset", "Resets configuration to default.") {
      configFile
        .flatMap { path =>
          io.file.deleteIfExists(blocker, path).as(ExitCode.Success)
        }
        .pure[Opts]
    }

  def show: Opts[F[ExitCode]] =
    Opts.subcommand("show", "Shows the current configuration.") {
      loadConfig
        .flatMap { cfg =>
          val opts   = ConfigRenderOptions.concise().setComments(false).setJson(false).setFormatted(true)
          val string = ConfigWriter[BenchConfig].to(cfg).render(opts)
          F.delay(println(string)).as(ExitCode.Success)
        }
        .pure[Opts]
    }

  private def loadConfig: F[BenchConfig] =
    (defaultConfigSource, customConfigSource).mapN((d, c) => c.withFallback(d)).flatMap { source =>
      F.fromEither(source.load[BenchConfig].leftMap(failures => ConfigurationError(failures)))
    }

  private def defaultConfigSource: F[ConfigObjectSource] =
    F.delay(ConfigSource.resources("bench.conf"))

  private def customConfigSource: F[ConfigObjectSource] =
    configFile.flatMap { path =>
      fileExists(path).ifM(
        F.delay(ConfigSource.file(path)),
        F.delay(ConfigSource.empty)
      )
    }

//  private def configStringFromFile(path: Path): F[Option[String]] =
//    fileExists(path).ifM(readEntireFile(path).map(Some.apply), F.pure(None))

  private def fileExists(path: Path): F[Boolean] =
    blocker.delay {
      val file = path.toFile
      file.exists() && file.isFile
    }

//  private def readEntireFile(path: Path): F[String] =
//    io.file
//      .readAll(path, blocker, 4096)
//      .through(text.utf8Decode)
//      .through(text.lines)
//      .compile
//      .fold(new StringBuilder)((builder, str) => builder.append(str))
//      .map(_.toString())

  private def configDir: F[Path] = {
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

  private def configFile: F[Path] =
    configDir.map(_.resolve("config"))
}

object Config {
  def apply[F[_]: Sync: ContextShift](blocker: Blocker): Opts[F[ExitCode]] =
    new Config[F](blocker).subcommand
}
