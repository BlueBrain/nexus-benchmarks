package io.gatling

import cats.effect.{ExitCode, Sync}
import ch.epfl.bluebrain.nexus.bench.tests.BaseSimulation
import io.gatling.app.Gatling

import scala.reflect.ClassTag

/**
  * Workaround the gatling package protected arg parser and executor.
  */
class SimulationRunner[F[_]: Sync] {

  def unsafeRunSync[A <: BaseSimulation: ClassTag]: ExitCode = {
    val className = implicitly[ClassTag[A]].runtimeClass.getCanonicalName
    ExitCode(Gatling.fromArgs(Array("-s", className), None))
  }

  def run[A <: BaseSimulation: ClassTag]: F[ExitCode] =
    Sync[F].delay(unsafeRunSync[A])

}

object SimulationRunner {
  def apply[F[_]: Sync]: SimulationRunner[F] =
    new SimulationRunner[F]
}
