package ch.epfl.bluebrain.nexus.bench

import cats.effect.*
import cats.effect.unsafe.IORuntime
import ch.epfl.bluebrain.nexus.bench.cli.Intent
import ch.epfl.bluebrain.nexus.bench.cli.TestName
import ch.epfl.bluebrain.nexus.bench.tests.{BaseSimulation, CreateNoValidationSimulation, ReadSimulation}
import io.gatling.SimulationRunner

object Test:

  def apply(test: Intent.Test)(using rt: IORuntime): IO[ExitCode] =
    BaseSimulation.intent = test
    BaseSimulation.runtime = rt
    test.test match
      case TestName.Read               => SimulationRunner.run[ReadSimulation]
      case TestName.CreateNoValidation => SimulationRunner.run[CreateNoValidationSimulation]
      case _                           => SimulationRunner.run[ReadSimulation]
