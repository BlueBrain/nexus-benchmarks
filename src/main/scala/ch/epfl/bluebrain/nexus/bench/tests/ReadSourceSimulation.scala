package ch.epfl.bluebrain.nexus.bench.tests

import io.gatling.core.Predef.*
import io.gatling.http.Predef.*

//noinspection TypeAnnotation
class ReadSourceSimulation extends BaseSimulation:

  val random = new java.util.Random()

  private def randProject      = s"proj${random.nextInt(BaseSimulation.intent.maxProjectIdx - 1) + 1}"
  private def randResource     = s"${encodedResBase}${random.nextInt(BaseSimulation.intent.maxResourceIdx - 1) + 1}"
  private def resourceIdSource = s"/resources/$org/$randProject/_/$randResource/source"

  val scn = scenario("ReadSourceSimulation")
    .forever {
      exec {
        http("getSourceById")
          .get(resourceIdSource)
          .check(status.in(200, 404))
      }
    }

  setupSimulation(scn)
