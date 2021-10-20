package ch.epfl.bluebrain.nexus.bench.tests

import ch.epfl.bluebrain.nexus.bench.cli.Intent
import ch.epfl.bluebrain.nexus.bench.tests.BaseSimulation.intent
import ch.epfl.bluebrain.nexus.bench.util.Classpath
import io.circe.Json
import io.gatling.core.Predef.*
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef.*
import io.gatling.http.protocol.HttpProtocolBuilder
import org.http4s.Uri.{Ipv4Address, Ipv6Address, RegName}
import org.http4s.headers.Authorization

import java.net.URLEncoder

abstract class BaseSimulation extends Simulation:

  val base: String            = BaseSimulation.intent.endpoint.renderString
  val org: String             = BaseSimulation.intent.organization
  val project: String         = BaseSimulation.intent.project
  val encodedResBase: String  = encode("https://nexus-sandbox.io/bench/resource")
  val encodedSchemaId: String = encode("https://neuroshapes.org/dash/stimulusexperiment")

  val resource: Json =
    Classpath.loadResourceAsJson("data/stimulusexperiment.json").unsafeRunSync()(using BaseSimulation.runtime)

  val httpProtocol: HttpProtocolBuilder =
    val withBase = intent.token match
      case Some(Authorization(credentials)) =>
        http.baseUrl(base).authorizationHeader(credentials.renderString)
      case None                             =>
        http.baseUrl(base)

//    if (config.env.ipAddresses.nonEmpty) {
//      val host = config.env.endpoint.host
//      val hostString = host flatMap {
//        case _: Ipv4Address    => None
//        case _: Ipv6Address    => None
//        case RegName(ciString) => Some(ciString.toString)
//      }
//      hostString match {
//        case Some(value) => withBase.hostNameAliases(Map(value -> config.env.ipAddresses.toList))
//        case None        => withBase
//      }
//    } else withBase
    withBase

  def encode(str: String): String =
    URLEncoder.encode(str, "UTF-8")

  def setupSimulation(scn: ScenarioBuilder): SetUp =
    setUp(
      scn
        .inject(atOnceUsers(BaseSimulation.intent.users))
        .protocols(httpProtocol)
    ).maxDuration(BaseSimulation.intent.duration)

object BaseSimulation:
  import cats.effect.unsafe.IORuntime
  var intent: Intent.Test = null
  var runtime: IORuntime  = null
