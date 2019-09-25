package ch.epfl.bluebrain.nexus.bench.cli

import cats.effect.{Blocker, ContextShift, ExitCode, Sync}
import cats.implicits._
import com.monovore.decline._

object Cli {

  def printHelp[F[_]](help: Help)(implicit F: Sync[F]): F[ExitCode] =
    F.delay(System.err.println(help)).as {
      if (help.errors.nonEmpty) ExitCode.Error
      else ExitCode.Success
    }

  def apply[F[_]: Sync: ContextShift](
      blocker: Blocker,
      args: List[String],
      env: Map[String, String] = Map.empty
  ): F[ExitCode] =
    Command("nxb", "Nexus Benchmark Tool")(Config[F](blocker))
      .parse(args, env)
      .fold(printHelp[F], identity)
}
