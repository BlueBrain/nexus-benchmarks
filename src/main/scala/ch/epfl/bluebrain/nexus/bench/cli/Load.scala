package ch.epfl.bluebrain.nexus.bench.cli

import cats.effect.{ConcurrentEffect, ContextShift, ExitCode, Timer}
import cats.implicits._
import ch.epfl.bluebrain.nexus.bench.BenchConfig
import ch.epfl.bluebrain.nexus.bench.BenchConfig.{EnvConfig, Exponential, Flat, Linear, LoadConfig}
import ch.epfl.bluebrain.nexus.bench.BenchError.{LoadDistributionNotImplemented, UnableToCreateResource}
import ch.epfl.bluebrain.nexus.bench.cli.Load.{Batch, Cursor, Progress}
import com.monovore.decline.Opts
import io.circe.Json
import io.circe.parser._
import fs2._
import org.http4s.Method._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.client.blaze._
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.headers._
import org.http4s.{MediaType, Status}

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._
import scala.io.Source

//noinspection DuplicatedCode
class Load[F[_]: ContextShift](cfg: Config[F], ec: ExecutionContext)(implicit F: ConcurrentEffect[F], T: Timer[F]) {

  def subcommand: Opts[F[ExitCode]] =
    Opts.subcommand("load", "Load data into the target system") {
      exec
    }

  def exec: Opts[F[ExitCode]] =
    Opts.apply {
      cfg.loadConfig.flatMap { bc =>
        withClient(bc.load) { client =>
          loadResource().flatMap { res =>
            bc.load.data match {
              case Exponential(resources) => exponential(client, resources, bc, res)
              case _: Flat =>
                F.raiseError(LoadDistributionNotImplemented("flat"))
              case _: Linear =>
                F.raiseError(LoadDistributionNotImplemented("linear"))
            }
          }
        }
      }
    }

  private val dsl = new Http4sClientDsl[F] {}
  import dsl._

  private def withClient(cfg: LoadConfig)(f: Client[F] => F[ExitCode]): F[ExitCode] =
    BlazeClientBuilder(ec)
      .withMaxTotalConnections(cfg.concurrency)
      .resource
      .use(f)

  private def exponential(client: Client[F], resources: Int, bc: BenchConfig, res: Json): F[ExitCode] = {
    val exponentialProjectSizes =
      (1 to 100).map { idx =>
        Math.pow(2d, (idx - 1).toDouble).toInt
      }

    def projectStream: F[Unit] = {
      val (maxIdx, _) = exponentialProjectSizes.foldLeft((0, 0)) {
        case ((currentIdx, globalSize), _) if globalSize > resources => (currentIdx, globalSize)
        case ((currentIdx, globalSize), currentSize)                 => (currentIdx + 1, globalSize + currentSize)
      }
      F.delay(println("Creating projects.")) >>
        Stream
          .range(1, maxIdx + 1)
          .covary[F]
          .mapAsync(5) { idx =>
            createProject(idx, bc.env, client)
          }
          .compile
          .drain >>
        F.delay(println("Projects created."))
    }

    def resourceStream: F[Unit] =
      Stream
        .range(1, resources + 1)
        .scan(Cursor(1, 1, 1)) {
          case (Cursor(pidx, residx, _), globalidx) =>
            if (residx == exponentialProjectSizes(pidx - 1)) Cursor(pidx + 1, 1, globalidx + 1)
            else Cursor(pidx, residx + 1, globalidx + 1)
        }
        .through(batch(bc.load.batch))
        .mapAsync(bc.load.concurrency) { b =>
          loadBatch(client, b, bc.env, res).map {
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

    F.delay(println()) >> createOrg(bc.env, client) >> projectStream >> resourceStream.as(ExitCode.Success)
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

  private def loadBatch(client: Client[F], batch: Batch, env: EnvConfig, res: Json): F[Status] = {
    val collectionSchema = "https://bluebrain.github.io/nexus/schemas/collection.json"
    val uri              = env.endpoint / "resources" / "bench" / s"project${batch.projectIdx}" / collectionSchema
    val body =
      Json.obj(
        "resources" -> Json.fromValues(
          batch.resourceIdxs.map { idx =>
            Json.obj(
              "resourceId" -> Json.fromString(s"https://nexus-sandbox.io/bench/resource$idx"),
              "schema"     -> Json.fromString("https://neuroshapes.org/dash/stimulusexperiment"),
              "resource"   -> res
            )
          }
        )
      )
    val req = env.token.authorization match {
      case Some(auth) => POST(body, uri, auth, accept)
      case None       => POST(body, uri, accept)
    }
    client.status(req)
  }

  private def createOrg(env: EnvConfig, client: Client[F]): F[Unit] = {
    val uri  = env.endpoint / "orgs" / "bench"
    val body = Json.obj()
    val req = env.token.authorization match {
      case Some(auth) => PUT(body, uri, auth, accept)
      case None       => PUT(body, uri, accept)
    }
    client.status(req).flatMap {
      case Status.Created | Status.Conflict => F.unit
      case other                            => F.raiseError(UnableToCreateResource(uri, Set(Status.Created, Status.Conflict), other))
    }
  }

  private def createProject(idx: Int, env: EnvConfig, client: Client[F]): F[Unit] = {
    val uri  = env.endpoint / "projects" / "bench" / s"project$idx"
    val body = Json.obj()
    val req = env.token.authorization match {
      case Some(auth) => PUT(body, uri, auth, accept)
      case None       => PUT(body, uri, accept)
    }
    client.status(req).flatMap {
      case Status.Created | Status.Conflict => F.unit
      case other                            => F.raiseError(UnableToCreateResource(uri, Set(Status.Created, Status.Conflict), other))
    }
  }

  private def loadResource(): F[Json] =
    F.delay {
        Source.fromInputStream(getClass.getClassLoader.getResourceAsStream("data/stimulusexperiment.json"), "UTF-8")
      }
      .flatMap { str =>
        F.fromEither(parse(str.getLines().mkString))
      }

  private def erasePreviousLine(): Unit = {
    print("\u001b[1A")
    print("\u001b[1000D")
    print("                                                                                                          ")
    print("\u001b[1000D")
    Console.flush()
  }

  private val accept = Accept(MediaType.application.json)

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
