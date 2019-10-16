package ch.epfl.bluebrain.nexus.bench.tests

import io.gatling.core.Predef._
import io.gatling.http.Predef._

//noinspection TypeAnnotation
class CreateNoValidationSimulation extends BaseSimulation {

  val stringResource = resource.noSpaces

  val scn = scenario("CreateNoValidationSimulation")
    .forever {
      exec {
        http("createResource")
          .post(s"$base/resources/$org/$project/")
          .body(StringBody(stringResource))
          .header(HttpHeaderNames.ContentType, HttpHeaderValues.ApplicationJson)
          .check(status.in(201))
      }
    }

  setupSimulation(scn)
}
