package ch.epfl.bluebrain.nexus.bench

import cats.effect.{ExitCode, IO}
import cats.implicits.*
import ch.epfl.bluebrain.nexus.bench.cli.Intent
import ch.epfl.bluebrain.nexus.bench.util.{Api, Classpath}
import io.circe.*
import org.http4s.Uri
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.implicits.*
import org.http4s.circe.CirceInstances.builder

import scala.annotation.tailrec

object PrepareTestData:

  def apply(intent: Intent.PrepareTestData): IO[ExitCode] =
    BlazeClientBuilder[IO].resource.use { client =>
      val api = Api(client, intent.endpoints, intent.token)
      for
        _                <- api.orgs.ensureExists(intent.organization)
        _                <- api.projects.ensureExists(intent.organization, "modular")
        ctx              <- Classpath.loadResourceAsJson("contexts/schema.json")
        _                <- api.resources.ensureExists(
                              intent.organization,
                              "modular",
                              uri"https://incf.github.io/neuroshapes/contexts/schema.json",
                              ctx
                            )
        schemas          <- readSchemasFromClasspath
        graph             = schemaGraph(schemas)
        sorted            = topSort(graph)
        _                <- forceUpdate(api, intent.organization, "modular", schemas, sorted)
        assembledSchema  <- Classpath.loadResourceAsJson("schemas/assembled/schema-expanded-framed.json")
        assembledSchemaid = uri"https://neuroshapes.org/dash/stimulusexperiment"
        _                <- api.projects.ensureExists(intent.organization, "assembled")
        _                <- forceUpdate(
                              api,
                              intent.organization,
                              "assembled",
                              Map(assembledSchemaid -> assembledSchema),
                              Vector(assembledSchemaid)
                            )
      yield ExitCode.Success
    }

  private def readSchemasFromClasspath: IO[Map[Uri, Json]] =
    BuildInfo.schemaIdx.toList
      .traverse { fileName =>
        val id = Uri.unsafeFromString(Uri.decode(fileName).replaceAll("\\.json", ""))
        Classpath.loadResourceAsJson(s"schemas/modular/$fileName").map(json => (id, json))
      }
      .map(_.to(Map))

  private def schemaGraph(map: Map[Uri, Json]): Map[Uri, Set[Uri]] =
    map.map { case (id, json) =>
      json.asArray match
        case None                       => (id, Set.empty[Uri])
        case Some(objs) if objs.isEmpty => (id, Set.empty[Uri])
        case Some(objs)                 =>
          objs.head.hcursor.downField("http://www.w3.org/2002/07/owl#imports").values match
            case None             => (id, Set.empty[Uri])
            case Some(importObjs) =>
              val imports = importObjs.foldLeft(Set.empty[Uri]) { case (acc, el) =>
                el.hcursor.get[Uri]("@id") match
                  case Left(_)    => acc
                  case Right(uri) => acc + uri
              }
              (id, imports)
    }

  private def topSort(graph: Map[Uri, Set[Uri]]): Vector[Uri] =
    @tailrec
    def inner(remaining: Map[Uri, Set[Uri]], done: Vector[Uri]): Vector[Uri] =
      val (noDeps, hasDeps) = remaining.partition { case (_, deps) => deps.isEmpty }
      if (noDeps.isEmpty)
        if (hasDeps.isEmpty) done
        else
          val graphString = hasDeps.map { case (uri, set) =>
            s"""${uri.renderString}:
                 |${set.map(e => s"  - ${e.renderString}").mkString(System.lineSeparator())}
                 |""".stripMargin
          }
          throw new IllegalArgumentException(s"Detected graph cycle:${System.lineSeparator()}$graphString")
      else
        val found = noDeps.map { case (uri, _) => uri }.toSet
        inner(hasDeps.view.mapValues(set => set -- found).toMap, done ++ found)
    inner(graph, Vector.empty)

  private def forceUpdate(api: Api, org: String, proj: String, schemas: Map[Uri, Json], ord: Vector[Uri]): IO[Unit] =
    ord.traverse { id =>
      api.schemas.revisionOf(org, proj, id).flatMap {
        case Some(rev) =>
          IO.println(s"Update schema: $id (rev $rev)") >>
            api.schemas.update(org, proj, id, rev, schemas(id))
        case None      =>
          IO.println(s"Create schema: $id") >>
            api.schemas.create(org, proj, id, schemas(id))
      }
    }.void

  given Decoder[Uri] =
    Decoder.decodeString.emap { str =>
      Uri.fromString(str).leftMap(_ => "Uri")
    }
