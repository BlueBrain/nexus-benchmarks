package ch.epfl.bluebrain.nexus.bench.tests

import java.net.URLEncoder

import cats.effect.{Blocker, ConcurrentEffect}
import ch.epfl.bluebrain.nexus.bench.BenchConfig
import ch.epfl.bluebrain.nexus.bench.cli.{Config, Load}
import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder
import monix.eval.Task
import monix.eval.instances.CatsConcurrentEffectForTask
import monix.execution.Scheduler

abstract class BaseSimulation extends Simulation {

  private implicit val scheduler: Scheduler = Scheduler.global
  private val options: Task.Options         = Task.defaultOptions.withSchedulerFeatures(scheduler)
  private implicit lazy val catsEffect: ConcurrentEffect[Task] =
    new CatsConcurrentEffectForTask()(scheduler, options)

  val config: BenchConfig = Blocker[Task]
    .use { blocker =>
      Config[Task](blocker).loadConfig
    }
    .runSyncUnsafe()

  val base: String           = config.env.endpoint.renderString
  val org: String            = config.env.org
  val project: String        = config.test.project
  val encodedResBase: String = encode(Load.resourceBase)

  val httpProtocol: HttpProtocolBuilder = http.baseUrl(base)

  def encode(str: String): String =
    URLEncoder.encode(str, "UTF-8")

  def setupSimulation(scn: ScenarioBuilder): SetUp =
    setUp(
      scn
        .inject(atOnceUsers(config.test.users))
        .protocols(httpProtocol)
    ).maxDuration(config.test.duration)

}
