package ch.epfl.bluebrain.nexus.bench.tests

import io.gatling.core.Predef._
import io.gatling.http.Predef._

//noinspection TypeAnnotation
class CreateSimulation extends BaseSimulation {

  val stringResource = resource.noSpaces

  val random = new java.util.Random()

  val scn = scenario("CreateSimulation")
    .forever {
      exec {
        http("createResource")
          .post(
            s"/resources/$org/$project/$encodedSchemaIdBase${random.nextInt(BaseSimulation.intent.maxTemplateSchemaIdx - 1) + 1}/"
          )
          .body(StringBody(stringResource))
          .header(HttpHeaderNames.ContentType, HttpHeaderValues.ApplicationJson)
          .check(status.in(201))
      }
    }

  setupSimulation(scn)
}
