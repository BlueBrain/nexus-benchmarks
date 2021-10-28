package ch.epfl.bluebrain.nexus.bench.tests

import io.gatling.core.Predef._
import io.gatling.http.Predef._

//noinspection TypeAnnotation
class ReadSimulation extends BaseSimulation:

  val random = new java.util.Random()

  private def randProject  = s"proj${random.nextInt(BaseSimulation.intent.maxProjectIdx - 1) + 1}"
  private def randResource = s"${encodedResBase}${random.nextInt(BaseSimulation.intent.maxResourceIdx - 1) + 1}"
  private def resourceId   = s"/resources/$org/$randProject/_/$randResource"

  val scn = scenario("ReadSimulation")
    .forever {
      exec {
        http("getById")
          .get(resourceId)
          .check(status.in(200, 404))
      }
    }

  setupSimulation(scn)
