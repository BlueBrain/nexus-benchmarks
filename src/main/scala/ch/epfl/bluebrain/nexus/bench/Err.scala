package ch.epfl.bluebrain.nexus.bench

import cats.effect.IO
import cats.implicits.*
import com.monovore.decline.Help
import org.http4s.DecodeFailure
import fansi.Color
import fansi.Str

import java.util.regex.Pattern
import scala.util.Try

/**
  * Benchmark error enumeration.
  *
  * @param message
  *   a short descriptive message as to why the error occurred
  * @param description
  *   a (possibly multi-line) description of the error
  * @param solution
  *   and optional solution for solving this error
  */
enum Err(val message: String, val description: String, val solution: Option[String]) extends Exception:
  case ConfigErr
      extends Err(
        "The 'user.home' system property was not defined.",
        s"""The 'user.home' property is required for determining where the application
           |configuration needs to be stored or read from. The JVM automatically detects
           |the appropriate value from the provided environment, but in this case it could not.""".stripMargin,
        Some("run the tool again forcing a value ('-Duser.home=') to the expected system property.")
      )

  case CliErr(help: Help)
      extends Err(
        help.errors.mkString(", "),
        System.lineSeparator() + "Usage: " + System.lineSeparator() + help.usage.mkString(
          System.lineSeparator()
        ) + help.body.mkString(System.lineSeparator()),
        None
      )

  case UnknownErr(th: Throwable)
      extends Err(
        Try(th.getClass.getCanonicalName + ": " + th.getMessage).getOrElse(th.getClass.getCanonicalName),
        th.getStackTrace.map(_.toString).mkString("\n"),
        None
      )

  case ResourceErr(path: String)
      extends Err(
        s"The resource '$path' could not be loaded from the classpath.",
        s"""The inability to load a resource from the classpath implies that there was
           |a packaging error, as the file is expected to be bundled along with the tool.""".stripMargin,
        Some("contact the developers and ask them to fix this issue.")
      )

  case ResourceParseErr(path: String, cause: String)
      extends Err(
        s"The resource '$path' loaded from the classpath could not be parsed.",
        s"""The inability to parse a resource from the classpath implies that there was
           |a packaging error, as the correct file is expected to be bundled along with the tool.
           |The underlying parsing error message is '$cause'""".stripMargin,
        Some("contact the developers and ask them to fix this issue.")
      )

  case DecodeErr(cause: String)
      extends Err(
        "Failed to decode a response from the server.",
        s"The underlying decoding failure message is: $cause",
        None
      )

  case UnexpectedResponse(code: Int, string: String)
      extends Err(
        s"Received an unexpected response from the server.",
        s"""The response status code was: $code.
           |The response as string was:
           |$string""".stripMargin,
        None
      )

  override def fillInStackTrace(): Throwable = this
  override def getMessage: String            = message

  /**
    * Renders the error as a String.
    */
  def render(term: Terminal): IO[String] =
    this match
      case CliErr(help)            => IO.delay(help.toString)
      case UnknownErr(th)          =>
        for
          msg  <- term.render(Str("An error occurred: ") ++ Color.Red(message), Err.padding)
          desc <-
            th.getStackTrace.toList
              .traverse(e => term.render(e.toString, Err.padding))
              .map(_.mkString(System.lineSeparator()))
        yield msg + System.lineSeparator() + desc
      case err: UnexpectedResponse =>
        for
          msg  <- term.render(Str("An error occurred: ") ++ Color.Red(message), Err.padding)
          desc <-
            description
              .split(System.lineSeparator())
              .toList
              .traverse(line => term.render(line, Err.padding))
              .map(_.mkString(System.lineSeparator()))
        yield msg + System.lineSeparator() + desc
      case err                     =>
        for
          msg  <- term.render(Str("An error occurred: ") ++ Color.Red(message), Err.padding)
          desc <- term.render(Color.Cyan("Description: ") ++ Str(description), Err.padding)
          sol  <- term.render(
                    solution match
                      case Some(sol) => Color.Green("Solution: ") ++ Str(sol)
                      case None      => Str("")
                    ,
                    Err.padding
                  )
        yield msg + System.lineSeparator() + desc + System.lineSeparator() + sol

  /**
    * Prints the error as a String.
    */
  def println(term: Terminal): IO[Unit] =
    render(term).flatMap { str =>
      term.write(str)
    }
end Err

object Err:
  private val padding = Str("🔥  ")
end Err
