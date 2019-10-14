package ch.epfl.bluebrain.nexus.bench.cli

import cats.effect.{ExitCode, Sync}
import cats.implicits._
import ch.epfl.bluebrain.nexus.bench.tests.{CreateNoValidationSimulation, CreateSimulation, ReadSimulation, ReadSourceSimulation}
import com.monovore.decline.Opts
import io.gatling.SimulationRunner

class Test[F[_]: Sync] {

  def subcommand: Opts[F[ExitCode]] =
    Opts.subcommand("test", "Execute benchmarks") {
      read orElse readSource orElse create orElse createNoValidation
    }

  def read: Opts[F[ExitCode]] =
    Opts.subcommand("read", "Execute the read simulation") {
      SimulationRunner[F].run[ReadSimulation].pure[Opts]
    }

  def readSource: Opts[F[ExitCode]] =
    Opts.subcommand("read-source", "Execute the read source simulation") {
      SimulationRunner[F].run[ReadSourceSimulation].pure[Opts]
    }

  def create: Opts[F[ExitCode]] =
    Opts.subcommand("create", "Execute the create simulation") {
      SimulationRunner[F].run[CreateSimulation].pure[Opts]
    }

  def createNoValidation: Opts[F[ExitCode]] =
    Opts.subcommand("create-no-validation", "Execute the create simulation without validation") {
      SimulationRunner[F].run[CreateNoValidationSimulation].pure[Opts]
    }
}

object Test {
  def apply[F[_]: Sync]: Test[F] = new Test
}
