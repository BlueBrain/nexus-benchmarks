package ch.epfl.bluebrain.nexus.bench

import org.jline.terminal.{Terminal => JTerminal}
import org.jline.terminal.TerminalBuilder
import cats.effect.*
import cats.effect.kernel.Resource
import fansi.Str

import scala.annotation.tailrec

class Terminal private(underlying: JTerminal):

  def width: IO[Int] =
    IO.delay(underlying.getWidth)

  def height: IO[Int] =
    IO.delay(underlying.getHeight)

  def render(string: Str, padding: Str = Str("")): IO[String] =
    def inner(builder: StringBuilder, written: Int, remaining: Vector[Str], maxWidth: Int, paddingLength: Int): String =
      if (written == 0 && paddingLength != 0)
        builder.append(padding.render)
        inner(builder, paddingLength, remaining, maxWidth, paddingLength)
      else
        remaining match
          case head +: tail =>
            if (head.length + written >= maxWidth && written != paddingLength)
              builder.append(System.lineSeparator()).append(padding.render)
              inner(builder, paddingLength, remaining, maxWidth, paddingLength)
            else
              builder.append(head.render).append(' ')
              inner(builder, written + head.length + 1, tail, maxWidth, paddingLength)
          case _ => builder.toString()
    width.map { w =>
      val tokens = tokenize(string)
      val paddingLength = padding.length
      inner(new StringBuilder, 0, tokens, w, paddingLength)
    }

  def write(string: Str, padding: Str = Str("")): IO[Unit] =
    render(string, padding).flatMap { str =>
      write(str)
    }

  def write(string: String): IO[Unit] =
    IO.delay {
      val writer = underlying.writer()
      writer.println(string)
      writer.flush()
    }

  private def tokenize(string: Str): Vector[Str] =
    @tailrec
    def inner(acc: Vector[Str], remaining: Str): Vector[Str] =
      val nextIdx = remaining.plainText.indexWhere(c => c == ' ' || c == '\r' || c == '\n')
      if (nextIdx == -1)
        if (remaining.length == 0) acc
        else acc :+ remaining
      else if (nextIdx == 0) inner(acc, remaining.substring(1))
      else
        val (left, right) = remaining.splitAt(nextIdx)
        inner(acc :+ left, right.substring(1))
    inner(Vector.empty, string)

object Terminal:

  def apply(): Resource[IO, Terminal] =
    Resource.fromAutoCloseable(IO.delay(TerminalBuilder.terminal())).map(t => new Terminal(t))
