package ch.epfl.bluebrain.nexus.bench.cli

import cats.effect.{ExitCode, Sync}
import cats.implicits._
import ch.epfl.bluebrain.nexus.bench.tests.ReadSimulation
import com.monovore.decline.Opts
import io.gatling.SimulationRunner

class Test[F[_]: Sync] {

  def subcommand: Opts[F[ExitCode]] =
    Opts.subcommand("test", "Execute benchmarks") {
      read
    }

  def read: Opts[F[ExitCode]] =
    Opts.subcommand("read", "Execute the read simulation") {
      SimulationRunner[F].run[ReadSimulation].pure[Opts]
    }

}

object Test {
  def apply[F[_]: Sync]: Test[F] = new Test
}
