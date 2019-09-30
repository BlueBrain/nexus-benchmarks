package ch.epfl.bluebrain.nexus.bench

import cats.implicits._
import ch.epfl.bluebrain.nexus.bench.BenchConfig._
import org.http4s.Uri
import pureconfig.error.{CannotConvert, ConvertFailure}
import pureconfig.generic.semiauto._
import pureconfig.syntax._
import pureconfig.{ConfigConvert, ConfigReader, ConfigWriter}

import scala.concurrent.duration.FiniteDuration

case class BenchConfig(
    env: EnvConfig,
    load: LoadConfig,
    test: TestConfig
)

object BenchConfig {

  sealed trait TokenConfig
  case class Token(value: String) extends TokenConfig
  case object NoToken             extends TokenConfig

  object TokenConfig {
    implicit val tokenConfigConvert: ConfigConvert[TokenConfig] =
      ConfigConvert[String].xmap[TokenConfig]({
        case "none" => NoToken
        case other  => Token(other)
      }, {
        case Token(value) => value
        case NoToken      => "none"
      })
  }

  case class EnvConfig(
      token: TokenConfig,
      endpoint: Uri
  )

  object EnvConfig {
    implicit val uriConfigReader: ConfigReader[Uri] =
      ConfigReader[String].emap { str =>
        Uri.fromString(str).leftMap(err => CannotConvert(str, classOf[Uri].getCanonicalName, err.details))
      }
    implicit val uriConfigWriter: ConfigWriter[Uri] =
      ConfigWriter[String].contramap(_.renderString)
    implicit val envConfigConvert: ConfigConvert[EnvConfig] =
      deriveConvert[EnvConfig]
  }

  sealed trait DataConfig
  case class Exponential(resources: Int)                      extends DataConfig
  case class Flat(resources: Int, projects: Int)              extends DataConfig
  case class Linear(resources: Int, projects: Int, seed: Int) extends DataConfig

  object DataConfig {

    implicit val dataConfigReader: ConfigReader[DataConfig] =
      ConfigReader.fromCursor[DataConfig] { cursor =>
        cursor.asObjectCursor.flatMap { c =>
          val distributionCursor = c.atKeyOrUndefined("distribution")
          val distribution       = distributionCursor.to[String]
          val resources          = c.atKeyOrUndefined("resources").to[Int]
          distribution.flatMap {
            case "exponential" =>
              resources.map(Exponential.apply)
            case "flat" =>
              val projects = c.atKeyOrUndefined("projects").to[Int]
              (resources, projects).mapN((r, p) => Flat(r, p))
            case "linear" =>
              val projects = c.atKeyOrUndefined("projects").to[Int]
              val seed     = c.atKeyOrUndefined("seed").to[Int]
              (resources, projects, seed).mapN((r, p, s) => Linear(r, p, s))
            case other =>
              ConfigReader.Result.fail(
                ConvertFailure(
                  CannotConvert(other, "distribution", s"The value '$other' is not a recognized distribution value."),
                  distributionCursor.location,
                  distributionCursor.path
                )
              )
          }
        }
      }

    implicit val exponentialConfigWriter: ConfigWriter[Exponential] =
      ConfigWriter.forProduct2("distribution", "resources")(exp => ("exponential", exp.resources))
    implicit val flatConfigWriter: ConfigWriter[Flat] =
      ConfigWriter.forProduct3("distribution", "resources", "projects")(
        flat => ("exponential", flat.resources, flat.projects)
      )
    implicit val linearConfigWriter: ConfigWriter[Linear] =
      ConfigWriter.forProduct4("distribution", "resources", "projects", "seed")(
        linear => ("linear", linear.resources, linear.projects, linear.seed)
      )
    implicit val dataConfigWriter: ConfigWriter[DataConfig] =
      ConfigWriter.fromFunction {
        case c: Exponential => exponentialConfigWriter.to(c)
        case c: Flat        => flatConfigWriter.to(c)
        case c: Linear      => linearConfigWriter.to(c)
      }
  }

  case class LoadConfig(concurrency: Int, batch: Int, validate: Boolean, data: DataConfig)

  object LoadConfig {
    implicit val loadConfigConvert: ConfigConvert[LoadConfig] =
      deriveConvert[LoadConfig]
  }

  case class TestConfig(duration: FiniteDuration)

  object TestConfig {
    implicit val testConfigConvert: ConfigConvert[TestConfig] =
      deriveConvert[TestConfig]
  }

  implicit val benchConfigConvert: ConfigConvert[BenchConfig] =
    deriveConvert[BenchConfig]
}
