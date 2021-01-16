package ch.epfl.bluebrain.nexus.bench.cli

import cats.effect.{ConcurrentEffect, ContextShift, ExitCode, Timer}
import cats.implicits._
import ch.epfl.bluebrain.nexus.bench.BenchConfig
import ch.epfl.bluebrain.nexus.bench.BenchConfig.{EnvConfig, Exponential, Flat, Linear, LoadConfig}
import ch.epfl.bluebrain.nexus.bench.BenchError.{LoadDistributionNotImplemented, UnableToCreateResource}
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
import org.http4s.{EntityDecoder, MediaType, Status}
import ch.epfl.bluebrain.nexus.bench.cli.CliOpts._
import ch.epfl.bluebrain.nexus.bench.cli.Load._

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._
import scala.io.Source
import scala.util.control.NonFatal

//noinspection DuplicatedCode
class Load[F[_]: ContextShift](cfg: Config[F], ec: ExecutionContext)(implicit F: ConcurrentEffect[F], T: Timer[F]) {

  def subcommand: Opts[F[ExitCode]] =
    Opts.subcommand("load", "Load data into the target system") {
      exec
    }

  def exec: Opts[F[ExitCode]] =
    startIdx.withDefault(1).map { si =>
      cfg.loadConfig.flatMap { bc =>
        withClient(bc.load) { client =>
          loadResource().flatMap { res =>
            bc.load.data match {
              case Exponential(resources) => exponential(client, resources, bc, res, si)
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

  private def exponential(client: Client[F], resources: Int, bc: BenchConfig, res: Json, start: Int): F[ExitCode] = {
    val exponentialProjectSizes =
      (1 to 50).map { idx =>
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

    val startCursor =
      if (start == 1) Cursor(1, 1, 1)
      else {
        exponentialProjectSizes.foldLeft((Cursor(0, 1, 0), start)) {
          case ((Cursor(pidx, ridx, gidx), remaining), size) =>
              if (size > remaining) (Cursor(pidx, ridx + remaining, gidx + remaining), 0)
              else (Cursor(pidx + 1, 1, gidx + size), remaining - size)
        }
      }

    def resourceStream: F[Unit] =
      Stream
        .range(start, resources + 1)
        .scan(startCursor) {
          case (Cursor(pidx, residx, _), globalidx) =>
            if (residx == exponentialProjectSizes(pidx - 1)) Cursor(pidx + 1, 1, globalidx + 1)
            else Cursor(pidx, residx + 1, globalidx + 1)
        }
        .covary[F]
        .mapAsync(bc.load.concurrency) {
          case Cursor(projectIdx, resourceIdx, globalIdx) =>
            if (globalIdx < start)
              F.pure((projectIdx, resourceIdx, globalIdx, 0)) // skip resource until the global index reaches the start idx
            else
              loadResource(client, bc, res, projectIdx, resourceIdx).map {
                case Status.Created | Status.Conflict => (projectIdx, resourceIdx, globalIdx, 0)
                case _                                => (projectIdx, resourceIdx, globalIdx, 1)
              }
        }
        .mapAccumulate((0, System.currentTimeMillis())) {
          case ((errors, startTs), (projectIdx, resourceIdx, globalIdx, newErrors)) =>
            val progress = Progress(projectIdx, resourceIdx, globalIdx, errors + newErrors, startTs)
            ((errors + newErrors, startTs), progress)
        }
        .debounce(500.millis)
        .mapAsync(1) {
          case (_, progress @ Progress(projectIdx, resourceIdx, globalIdx, errors, startTs)) =>
            F.delay {
              erasePreviousLine()
              val elapsed = (System.currentTimeMillis() - startTs) / 1000 //seconds
              val rate    = if (elapsed == 0 || globalIdx < start) 0 else (globalIdx - start) / elapsed
              println(
                s"Project $projectIdx - resources: $resourceIdx, total: $globalIdx, errors: $errors, rate: $rate / sec, elapsed: $elapsed sec"
              )
              progress
            }
        }
        .compile
        .last
        .flatMap {
          case Some(Progress(pidx, _, globalidx, errors, _)) =>
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

  private def loadResource(
      client: Client[F],
      bc: BenchConfig,
      res: Json,
      projectIdx: Int,
      resourceIdx: Int
  ): F[Status] = {
    val uri = bc.env.endpoint / "resources" / bc.env.org / s"project$projectIdx" / "_" / s"$resourceBase$resourceIdx"
    val req = bc.env.token.authorization match {
      case Some(auth) => PUT(res, uri, auth, accept)
      case None       => PUT(res, uri, accept)
    }
    val fa = client.run(req).use { resp =>
      implicitly[EntityDecoder[F, Json]]
        .decode(resp, strict = false)
        .value
        .map {
          case _ if resp.status.isSuccess || resp.status == Status.Conflict =>
            resp.status
          case Left(fail) =>
            println(fail)
            resp.status
          case Right(json) =>
            println(json.spaces2)
            resp.status
        }
    }
    retry(fa, 20)
  }

  private def createOrg(env: EnvConfig, client: Client[F]): F[Unit] = {
    val uri  = env.endpoint / "orgs" / env.org
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
    val uri  = env.endpoint / "projects" / env.org / s"project$idx"
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

  def loadResource(): F[Json] =
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

  private def retry[A](fa: F[A], max: Int): F[A] = {
    def inner(remaining: Int): F[A] =
      fa.handleErrorWith {
        case NonFatal(th) if remaining > 0 =>
          F.delay(
            println(s"An error has occurred: ${th.getClass.getSimpleName}, retrying ($remaining retries left)...")
          ) >>
            T.sleep(500.millis) >>
            inner(remaining - 1)
        case NonFatal(th) =>
          F.delay(
            println(
              s"An error has occurred: ${th.getClass.getSimpleName}, but reached max retries ($max), giving up..."
            )
          ) >>
            F.raiseError(th)
      }
    inner(max)
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
    def toProgress(errors: Int, startTs: Long): Progress =
      Progress(projectIdx, resourceIdxs.lastOption.getOrElse(0), globalIdx, errors, startTs)
  }
  case class Progress(projectIdx: Int, resourceIdx: Int, globalIdx: Int, errors: Int, start: Long)

  val resourceBase: String = "https://nexus-sandbox.io/bench/resource"
}
