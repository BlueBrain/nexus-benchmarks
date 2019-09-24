package ch.epfl.bluebrain.nexus.bench

import cats.Show
import pureconfig.error.ConfigReaderFailures

sealed trait BenchError extends Exception {
  override def fillInStackTrace(): BenchError = this
  def reason: String
  def lines: List[String]
  def footer: Option[String] = None
}

object BenchError {

  case class ConfigurationError(failures: ConfigReaderFailures) extends BenchError {
    override val reason: String = "the application configuration failed to load"
    override val lines: List[String] =
      failures.toList.flatMap { f =>
        f.location match {
          case Some(loc) => f.description :: s"  file: ${loc.url.toString}" :: s"  line: ${loc.lineNumber}" :: Nil
          case None      => f.description :: Nil
        }
      }
  }

  implicit val serviceErrorShow: Show[BenchError] = Show.show { err =>
    s"""🔥  A service error occurred because '${Console.RED}${err.reason}${Console.RESET}', details:
       |🔥
       |${err.lines.map(l => s"🔥    $l").mkString("\n")}${err.footer
         .map(l => s"\n🔥  $l")
         .mkString("\n")}""".stripMargin
  }

}
