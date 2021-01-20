package ch.epfl.bluebrain.nexus.bench.tests

import cats.effect.{Blocker, ConcurrentEffect}
import ch.epfl.bluebrain.nexus.bench.BenchConfig
import ch.epfl.bluebrain.nexus.bench.cli.{Config, Load}
import io.circe.Json
import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder
import monix.eval.Task
import monix.eval.instances.CatsConcurrentEffectForTask
import monix.execution.Scheduler
import org.http4s.Uri.{Ipv4Address, Ipv6Address, RegName}

import java.net.URLEncoder

abstract class BaseSimulation extends Simulation {

  private implicit val scheduler: Scheduler = Scheduler.global
  private val options: Task.Options         = Task.defaultOptions.withSchedulerFeatures(scheduler)
  private implicit lazy val catsEffect: ConcurrentEffect[Task] =
    new CatsConcurrentEffectForTask()(scheduler, options)

  val (config: BenchConfig, resource: Json) = Blocker[Task]
    .use { blocker =>
      val cfg  = Config[Task](blocker)
      val load = Load[Task](cfg)
      for {
        bc  <- cfg.loadConfig
        res <- load.loadResource()
      } yield (bc, res)
    }
    .runSyncUnsafe()

  val base: String           = config.env.endpoint.renderString
  val org: String            = config.env.org
  val project: String        = config.test.project
  val encodedResBase: String = encode(Load.resourceBase)

  val encodedSchemaId: String = encode("https://neuroshapes.org/dash/stimulusexperiment")

  val httpProtocol: HttpProtocolBuilder = {
    val withBase = config.env.token match {
      case BenchConfig.Token(value) =>
        http.baseUrl(config.env.endpoint.renderString).authorizationHeader(s"Bearer $value")
      case BenchConfig.NoToken =>
        http.baseUrl(config.env.endpoint.renderString)
    }

    if (config.env.ipAddresses.nonEmpty) {
      val host = config.env.endpoint.host
      val hostString = host flatMap {
        case _: Ipv4Address    => None
        case _: Ipv6Address    => None
        case RegName(ciString) => Some(ciString.toString)
      }
      hostString match {
        case Some(value) => withBase.hostNameAliases(Map(value -> config.env.ipAddresses.toList))
        case None        => withBase
      }
    } else withBase
  }

  def encode(str: String): String =
    URLEncoder.encode(str, "UTF-8")

  def setupSimulation(scn: ScenarioBuilder): SetUp =
    setUp(
      scn
        .inject(atOnceUsers(config.test.users))
        .protocols(httpProtocol)
    ).maxDuration(config.test.duration)

}
