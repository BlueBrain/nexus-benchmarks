package ch.epfl.bluebrain.nexus.bench.tests

import io.gatling.core.Predef._
import io.gatling.http.Predef._

//noinspection TypeAnnotation
class CreateSimulation extends BaseSimulation {

  val stringResource = resource.noSpaces

  val scn = scenario("CreateSimulation")
    .forever {
      exec {
        http("createResource")
          .post(s"$base/resources/$org/$project/$encodedSchemaId/")
          .body(StringBody(stringResource))
          .header(HttpHeaderNames.ContentType, HttpHeaderValues.ApplicationJson)
          .check(status.in(201))
      }
    }

  setupSimulation(scn)
}
