package ch.epfl.bluebrain.nexus.bench

import cats.Show
import org.http4s.{Status, Uri}
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

  case class LoadDistributionNotImplemented(tpe: String) extends BenchError {
    override val reason: String = s"the load distribution '$tpe' is not yet implemented"
    override def lines: List[String] = List(
      s"The load distribution '$tpe' is not yet implemented.",
      "",
      s"${Console.GREEN}Solution${Console.RESET}: please use an '${Console.CYAN}exponential${Console.RESET}' load distribution."
    )
  }

  case object UserHomeNotDefined extends BenchError {
    override def reason: String = "the 'user.home' system property was not defined"
    override def lines: List[String] = List(
      "The 'user.home' property is required for determining where the application",
      "configuration needs to be stored or read from. The JVM automatically detects",
      "the appropriate value from the provided environment, but in this case it",
      "could not.",
      "",
      s"${Console.GREEN}Solution${Console.RESET}: run the tool again forcing a value ('${Console.CYAN}-Duser.home=${Console.RESET}') to the",
      "expected system property."
    )
  }

  case class IllegalPath(representation: String) extends BenchError {
    override def reason: String = "a string could not be parsed as a valid path"
    override def lines: List[String] = List(
      "The following string could not be parsed as a 'Path':",
      s"\t'$representation'",
      "",
      s"${Console.GREEN}Solution${Console.RESET}: identify the source of this path representation to correct it and",
      "run the tool again."
    )
  }

  case class UnableToCreateResource(uri: Uri, expected: Set[Status], actual: Status) extends BenchError {
    override def reason: String = s"unable to create resource at ${uri.renderString}"
    override def lines: List[String] = List(
      s"The server did not return one of the expected status codes (${expected.map(_.renderString).mkString(", ")})",
      s"but ${actual.renderString}."
    )
  }

  implicit val serviceErrorShow: Show[BenchError] = Show.show { err =>
    s"""🔥  A service error occurred because '${Console.RED}${err.reason}${Console.RESET}', details:
       |🔥
       |${err.lines.map(l => s"🔥    $l").mkString("\n")}${err.footer
         .map(l => s"\n🔥  $l")
         .mkString("\n")}""".stripMargin
  }

}
