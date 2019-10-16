package ch.epfl.bluebrain.nexus.bench.tests

import io.gatling.core.Predef._
import io.gatling.http.Predef._

//noinspection TypeAnnotation
class ReadSourceSimulation extends BaseSimulation {

  val random = new java.util.Random()

  private def resourceIdSource =
    s"$base/resources/$org/$project/$encodedSchemaId/${encodedResBase}${random.nextInt(config.test.maxResourceIndex)}/source"

  val scn = scenario("ReadSourceSimulation")
    .forever {
      exec {
        http("getSourceById")
          .get(resourceIdSource)
          .check(status.in(200, 404))
      }
    }

  setupSimulation(scn)
}
