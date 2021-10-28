package ch.epfl.bluebrain.nexus.bench.tests

import io.gatling.core.Predef._
import io.gatling.http.Predef._

//noinspection TypeAnnotation
class ReadSimulation extends BaseSimulation:

  val random = new java.util.Random()

  private def randIdx =
    val value = random.nextInt(BaseSimulation.intent.maxProjectIdx + 1)
    if (value == 0) 1
    else value

  private def randProject  = s"proj$randIdx"
  private def randResource = s"${encodedResBase}${random.nextInt(BaseSimulation.intent.maxResourceIdx)}"
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
