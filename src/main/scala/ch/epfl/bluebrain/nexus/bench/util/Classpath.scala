package ch.epfl.bluebrain.nexus.bench.util

import cats.effect.IO
import cats.implicits.*
import ch.epfl.bluebrain.nexus.bench.Err
import ch.epfl.bluebrain.nexus.bench.Inject.getClass
import fs2.text
import io.circe.Json
import io.circe.parser.*

object Classpath:

  def loadResource(path: String): IO[String] =
    IO.delay(ctxClassLoader.getResourceAsStream(path))
      .flatMap { is =>
        if (is == null) IO.raiseError(Err.ResourceErr(path))
        else
          fs2.io
            .readInputStream(IO.pure(is), 4096, true)
            .through(text.utf8.decode)
            .through(text.lines)
            .compile
            .toList
            .map {
              _.foldLeft("")(_ + _)
            }
      }

  def loadResourceAsJson(path: String): IO[Json] =
    loadResource(path).flatMap { str =>
      IO.fromEither(parse(str).leftMap(pf => Err.ResourceParseErr(path, pf.message)))
    }

  private def ctxClassLoader: ClassLoader =
    Thread.currentThread().getContextClassLoader
