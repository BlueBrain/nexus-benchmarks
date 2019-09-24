package ch.epfl.bluebrain.nexus.bench

import cats.effect.{ConcurrentEffect, ExitCode, Timer}
import cats.implicits._
import monix.eval.{Task, TaskApp}

object Main extends TaskApp {

  private def runF[F[_]: Timer](args: List[String])(implicit F: ConcurrentEffect[F]): F[ExitCode] = {
    args.foreach(_ => ())

    val program = for {
      _ <- F.delay(println("Hello world!"))
    } yield ExitCode.Success

    program.onError {
      case se: BenchError => F.delay(println(se.show))
    }
  }

  override def run(args: List[String]): Task[ExitCode] =
    runF[Task](args)

}
