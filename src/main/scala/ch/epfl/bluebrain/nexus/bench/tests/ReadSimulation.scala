package ch.epfl.bluebrain.nexus.bench.tests

import io.gatling.core.Predef._
import io.gatling.http.Predef._

//noinspection TypeAnnotation
class ReadSimulation extends BaseSimulation {

  val random = new java.util.Random()

  private def resourceId =
    s"$base/resources/$org/$project/$encodedSchemaId/${encodedResBase}${random.nextInt(config.test.maxResourceIndex)}"

  val scn = scenario("ReadSimulation")
    .forever {
      exec {
        http("getById")
          .get(resourceId)
          .check(status.in(200, 404))
      }
    }

  setupSimulation(scn)
}
