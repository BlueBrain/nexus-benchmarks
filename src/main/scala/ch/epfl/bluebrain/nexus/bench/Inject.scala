package ch.epfl.bluebrain.nexus.bench

import cats.effect.*
import ch.epfl.bluebrain.nexus.bench.cli.Intent
import io.circe.*
import fs2.*

object Inject:

  def apply(inject: Intent.Inject): IO[ExitCode] =
    Stream.eval(IO.realTimeInstant).flatMap { startInstant =>
      Stream.range(inject.startIdx, inject.resourceCount).covary[IO]

    }

    IO.println(inject).as(ExitCode.Success)

  def projectFor(index: Int): Int = ???
