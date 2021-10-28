package ch.epfl.bluebrain.nexus.bench.tests

import io.gatling.core.Predef.*
import io.gatling.http.Predef.*

//noinspection TypeAnnotation
class ReadSourceSimulation extends BaseSimulation:

  val random = new java.util.Random()

  private def randIdx =
    val value = random.nextInt(BaseSimulation.intent.maxProjectIdx + 1)
    if (value == 0) 1
    else value

  private def randProject      = s"proj$randIdx"
  private def randResource     = s"${encodedResBase}${random.nextInt(BaseSimulation.intent.maxResourceIdx)}"
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
