package ch.epfl.bluebrain.nexus.bench.cli

import cats.effect.{ConcurrentEffect, ContextShift, ExitCode, Timer}
import cats.implicits._
import ch.epfl.bluebrain.nexus.bench.BenchConfig
import ch.epfl.bluebrain.nexus.bench.BenchConfig.{Exponential, Flat, Linear, LoadConfig}
import ch.epfl.bluebrain.nexus.bench.BenchError.LoadDistributionNotImplemented
import ch.epfl.bluebrain.nexus.bench.cli.Load.{Batch, Cursor, Progress}
import com.monovore.decline.Opts
import fs2._
import org.http4s.Status
import org.http4s.client.Client
import org.http4s.client.blaze._

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._
import scala.util.Random

class Load[F[_]: ContextShift](cfg: Config[F], ec: ExecutionContext)(implicit F: ConcurrentEffect[F], T: Timer[F]) {

  def subcommand: Opts[F[ExitCode]] =
    Opts.subcommand("load", "Load data into the target system") {
      exec
    }

  def exec: Opts[F[ExitCode]] =
    Opts.apply {
      cfg.loadConfig.flatMap { bc =>
        withClient(bc.load) { client =>
          bc.load.data match {
            case Exponential(resources) => exponential(client, resources, bc)
            case _: Flat =>
              F.raiseError(LoadDistributionNotImplemented("flat"))
            case _: Linear =>
              F.raiseError(LoadDistributionNotImplemented("linear"))
          }
        }
      }
    }

  private def withClient(cfg: LoadConfig)(f: Client[F] => F[ExitCode]): F[ExitCode] =
    BlazeClientBuilder(ec)
      .withMaxTotalConnections(cfg.concurrency)
      .resource
      .use(f)

  private def exponential(client: Client[F], resources: Int, bc: BenchConfig): F[ExitCode] = {
    val exponentialProjectSizes =
      (1 to 100).map { idx =>
        Math.pow(2d, (idx - 1).toDouble).toInt
      }
    F.delay(println()) >>
      Stream
        .range(1, resources + 1)
        .scan(Cursor(1, 1, 1)) {
          case (Cursor(pidx, residx, _), globalidx) =>
            if (residx == exponentialProjectSizes(pidx - 1)) Cursor(pidx + 1, 1, globalidx + 1)
            else Cursor(pidx, residx + 1, globalidx + 1)
        }
        .through(batch(bc.load.batch))
        .mapAsync(bc.load.concurrency) { b =>
          execute(client, b).map {
            case Status.Created | Status.Conflict => (b, 0)
            case _                                => (b, 1)
          }
        }
        .mapAccumulate(0) {
          case (errors, (batch, newErrors)) =>
            val progress = batch.toProgress(errors + newErrors)
            (errors + newErrors, progress)
        }
        .debounce(500.millis)
        .mapAsync(1) {
          case (_, progress @ Progress(projectIdx, resourceIdx, globalIdx, errors)) =>
            F.delay {
              erasePreviousLine()
              println(s"Project $projectIdx - resources: $resourceIdx, total: $globalIdx, errors: $errors")
              progress
            }
        }
        .compile
        .last
        .flatMap {
          case Some(Progress(pidx, _, globalidx, errors)) =>
            F.delay {
              println("Load finished.")
              println(s"Total projects: $pidx.")
              println(s"Total resources: ${globalidx - 1}.")
              println(s"Total errors: $errors.")
            } >> F.unit
          case _ => F.unit
        }
        .as(ExitCode.Success)
  }

  private def batch(max: Int): Pipe[F, Cursor, Batch] = {
    def go(s: Stream[F, Chunk[Cursor]]): Pull[F, Batch, Unit] =
      s.pull.uncons.flatMap {
        case Some((hd, tl)) =>
          val batches = hd.flatten.foldLeft[List[Batch]](Nil) {
            case (bhead :: btail, cursor) if bhead.projectIdx == cursor.projectIdx =>
              bhead.add(cursor) :: btail
            case (list, cursor) =>
              cursor.toBatch :: list
          }
          Pull.output(Chunk.iterable(batches.reverse)) >> go(tl)
        case None => Pull.done
      }
    in => go(in.chunkN(max, allowFewer = true)).stream
  }

  private def execute(client: Client[F], batch: Batch): F[Status] = {
    val _ = (client, batch).pure[F]
//    client.status(execute(batch))

    F.delay {
      if (Random.nextBoolean()) Status.Created
      else Status.InternalServerError
    }
  }

  private def erasePreviousLine(): Unit = {
    print("\u001b[1A")
    print("\u001b[1000D")
    print("                                                                                                          ")
    print("\u001b[1000D")
    Console.flush()
  }

}

object Load {
  def apply[F[_]: ContextShift: ConcurrentEffect: Timer](cfg: Config[F], ec: ExecutionContext = global): Load[F] =
    new Load(cfg, ec)

  case class Cursor(projectIdx: Int, resourceIdx: Int, globalIdx: Int) {
    def toBatch: Batch =
      Batch(projectIdx, Vector(resourceIdx), globalIdx)
  }
  case class Batch(projectIdx: Int, resourceIdxs: Vector[Int], globalIdx: Int) {
    def add(cursor: Cursor): Batch =
      copy(resourceIdxs = resourceIdxs :+ cursor.resourceIdx, globalIdx = cursor.globalIdx)
    def toProgress(errors: Int): Progress =
      Progress(projectIdx, resourceIdxs.lastOption.getOrElse(0), globalIdx, errors)
  }
  case class Progress(projectIdx: Int, resourceIdx: Int, globalIdx: Int, errors: Int)
}
