package ch.epfl.bluebrain.nexus.bench.cli

import cats.effect.{Blocker, ConcurrentEffect, ContextShift, ExitCode, Sync, Timer}
import cats.implicits._
import com.monovore.decline._

object Cli {

  def printHelp[F[_]](help: Help)(implicit F: Sync[F]): F[ExitCode] =
    F.delay(System.err.println(help)).as {
      if (help.errors.nonEmpty) ExitCode.Error
      else ExitCode.Success
    }

  def apply[F[_]: ContextShift: ConcurrentEffect: Timer](
      blocker: Blocker,
      args: List[String],
      env: Map[String, String] = Map.empty
  ): F[ExitCode] =
    Command("nxb", "Nexus Benchmark Tool") {
      val cfg  = Config[F](blocker)
      val load = Load[F](cfg)
      val test = Test[F]
      cfg.subcommand orElse load.subcommand orElse test.subcommand
    }.parse(args, env)
      .fold(printHelp[F], identity)
}
