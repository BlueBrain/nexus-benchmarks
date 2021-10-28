package io.gatling

import cats.effect.*
import ch.epfl.bluebrain.nexus.bench.tests.BaseSimulation
import io.gatling.app.Gatling

import scala.reflect.ClassTag

/**
  * Workaround the gatling package protected arg parser and executor.
  */
object SimulationRunner:
  def unsafeRunSync[A <: BaseSimulation: ClassTag]: ExitCode =
    val className = implicitly[ClassTag[A]].runtimeClass.getCanonicalName
    ExitCode(Gatling.fromArgs(Array("-s", className), None))

  def run[A <: BaseSimulation: ClassTag]: IO[ExitCode] =
    IO.delay(unsafeRunSync[A])
