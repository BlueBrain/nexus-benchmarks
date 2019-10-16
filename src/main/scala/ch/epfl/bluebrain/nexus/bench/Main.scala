package ch.epfl.bluebrain.nexus.bench

import cats.effect.{Blocker, ConcurrentEffect, ContextShift, ExitCode, Timer}
import cats.implicits._
import ch.epfl.bluebrain.nexus.bench.cli.Cli
import monix.eval.{Task, TaskApp}

object Main extends TaskApp {

  private def runF[F[_]: Timer: ContextShift](args: List[String])(implicit F: ConcurrentEffect[F]): F[ExitCode] =
    Blocker[F].use { blocker =>
      Cli[F](blocker, args).recoverWith {
        case se: BenchError => F.delay(println(se.show)).as(ExitCode.Error)
      }
    }

  override def run(args: List[String]): Task[ExitCode] =
    runF[Task](args)

}
