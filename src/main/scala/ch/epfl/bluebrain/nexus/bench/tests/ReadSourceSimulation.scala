package ch.epfl.bluebrain.nexus.bench.tests

import io.gatling.core.Predef.*
import io.gatling.http.Predef.*

//noinspection TypeAnnotation
class ReadSourceSimulation extends BaseSimulation:

  val random = new java.util.Random()

  private def resourceIdSource =
    s"/resources/$org/$project/_/${encodedResBase}${random.nextInt(BaseSimulation.intent.maxResourceIdx)}/source"

  val scn = scenario("ReadSourceSimulation")
    .forever {
      exec {
        http("getSourceById")
          .get(resourceIdSource)
          .check(status.in(200, 404))
      }
    }

  setupSimulation(scn)
