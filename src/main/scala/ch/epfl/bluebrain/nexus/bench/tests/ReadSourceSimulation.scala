package ch.epfl.bluebrain.nexus.bench.tests

import io.gatling.core.Predef._
import io.gatling.http.Predef._

//noinspection TypeAnnotation
class ReadSourceSimulation extends BaseSimulation {

  val resourceIds = Iterator
    .range(1, config.test.maxResourceIndex)
    .map(idx => Map("resourceId" -> s"$base/resources/$org/$project/_/${encodedResBase}${idx}"))

  val scn = scenario("ReadSourceSimulation")
    .forever {
      feed(resourceIds)
        .exec {
          http("getById")
            .get("${resourceId}/source")
            .check(status.in(200, 404))
        }
    }

  setupSimulation(scn)
}
