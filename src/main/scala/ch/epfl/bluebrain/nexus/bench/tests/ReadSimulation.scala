package ch.epfl.bluebrain.nexus.bench.tests

import io.gatling.core.Predef._
import io.gatling.http.Predef._

//noinspection TypeAnnotation
class ReadSimulation extends BaseSimulation {

  val scn = scenario("ReadSimulation")
    .forever {
      exec(
        http("getById")
          .get(s"$base/resources/$org/$project/_/${encodedResBase}1")
          .check(status.in(200, 404))
      )
    }

  setupSimulation(scn)
}
