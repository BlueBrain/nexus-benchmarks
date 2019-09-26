package ch.epfl.bluebrain.nexus.bench

import cats.implicits._
import ch.epfl.bluebrain.nexus.bench.BenchConfig._
import org.http4s.Uri
import pureconfig.error.CannotConvert
import pureconfig.generic.semiauto._
import pureconfig.{ConfigConvert, ConfigReader, ConfigWriter}

import scala.concurrent.duration.FiniteDuration

case class BenchConfig(
    env: EnvConfig,
//    data: DataConfig,
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

  case class TestConfig(duration: FiniteDuration)

  object TestConfig {
    implicit val testConfigConvert: ConfigConvert[TestConfig] =
      deriveConvert[TestConfig]
  }

  implicit val benchConfigConvert: ConfigConvert[BenchConfig] =
    deriveConvert[BenchConfig]
}
