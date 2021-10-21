package ch.epfl.bluebrain.nexus.bench

import cats.effect.*
import ch.epfl.bluebrain.nexus.bench.cli.Intent
import ch.epfl.bluebrain.nexus.bench.util.{Api, Classpath}
import io.circe.*
import fs2.*
import org.http4s.Uri
import org.http4s.blaze.client.BlazeClientBuilder

import scala.concurrent.duration.*

object Inject:

  def apply(inject: Intent.Inject): IO[ExitCode] =
    BlazeClientBuilder[IO].withMaxTotalConnections(inject.concurrency).resource.use { client =>
      val api = Api(client, inject.endpoints, inject.token)

      for
        _        <- createOrg(inject, api)
        _        <- createProjects(inject, api)
        res      <- Classpath.loadResourceAsJson("data/stimulusexperiment.json")
        _        <- IO.println("Starting ingesting data.")
        progress <- Stream
                      .eval(IO.realTimeInstant)
                      .flatMap { startInstant =>
                        Stream
                          .range(inject.startIdx, inject.totalResourceCount)
                          .covary[IO]
                          .parEvalMap(inject.concurrency) { globalIdx =>
                            val projectIdx  = projectIdxFor(inject, globalIdx)
                            val resourceIdx = resourceIdxFor(inject, globalIdx)
                            api.resources
                              .create(
                                inject.organization,
                                s"proj$projectIdx",
                                Uri.unsafeFromString(s"https://nexus-sandbox.io/bench/resource$resourceIdx"),
                                res
                              )
                              .map { _ => (projectIdx, resourceIdx, globalIdx, 0) }
                              .handleError { _ => (projectIdx, resourceIdx, globalIdx, 1) }
                          }
                          .mapAccumulate(0) { case (errors, (projectIdx, resourceIdx, globalIdx, errorCount)) =>
                            (errors + errorCount, (projectIdx, resourceIdx, globalIdx))
                          }
                          .debounce(500.millis)
                          .evalTap { case (errors, (projectIdx, resourceIdx, globalIdx)) =>
                            IO.realTimeInstant.map { now =>
                              val elapsed = now.getEpochSecond - startInstant.getEpochSecond
                              val rate    = if (elapsed == 0) 0 else (globalIdx - inject.startIdx) / elapsed
                              erasePreviousLine()
                              println(
                                s"Project $projectIdx - resources: $resourceIdx, total: $globalIdx, errors: $errors, rate: $rate / sec, elapsed: $elapsed sec"
                              )
                            }
                          }
                      }
                      .compile
                      .last
        _        <- progress match
                      case Some((errors, (projectIdx, _, globalIdx))) =>
                        IO.println {
                          s"""Inject finished.
                             |Total projects: $projectIdx.
                             |Total resources: ${globalIdx - 1}.
                             |Total errors: $errors.
                             |""".stripMargin
                        }
                      case None                                       => IO.unit
      yield ExitCode.Success
    }

  def createOrg(inject: Intent.Inject, api: Api): IO[Unit] =
    IO.println(s"Creating organization: ${inject.organization}") >>
      api.orgs.ensureExists(inject.organization)

  def createProjects(inject: Intent.Inject, api: Api): IO[Unit] =
    val projectCount =
      if (inject.totalResourceCount % inject.projectResourceCount == 0)
        inject.totalResourceCount / inject.projectResourceCount
      else inject.totalResourceCount / inject.projectResourceCount + 1
    IO.println(s"Creating projects: proj[1..$projectCount].") >>
      Stream
        .range(1, projectCount + 1) // stop exclusive
        .evalMap { idx =>
          api.projects.ensureExists(inject.organization, s"proj$idx")
        }
        .compile
        .drain

  def projectIdxFor(inject: Intent.Inject, globalIdx: Int): Int =
    globalIdx / inject.projectResourceCount + 1

  def resourceIdxFor(inject: Intent.Inject, globalIdx: Int): Int =
    globalIdx % inject.projectResourceCount

  def erasePreviousLine(): Unit =
    print("\u001b[1A")
    print("\u001b[1000D")
    print("                                                                                                          ")
    print("\u001b[1000D")
    Console.flush()
