package ch.epfl.bluebrain.nexus.bench

import cats.effect.*
import cats.effect.unsafe.IORuntime
import ch.epfl.bluebrain.nexus.bench.cli.{Intent, TestName}
import ch.epfl.bluebrain.nexus.bench.tests.*
import io.gatling.SimulationRunner

object Test:

  def apply(test: Intent.Test)(using rt: IORuntime): IO[ExitCode] =
    BaseSimulation.intent = test
    BaseSimulation.runtime = rt
    test.test match
      case TestName.CreateNoValidation => SimulationRunner.run[CreateNoValidationSimulation]
      case TestName.Create             => SimulationRunner.run[CreateSimulation]
      case TestName.Read               => SimulationRunner.run[ReadSimulation]
      case TestName.ReadSource         => SimulationRunner.run[ReadSourceSimulation]
