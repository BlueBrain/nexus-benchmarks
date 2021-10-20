package ch.epfl.bluebrain.nexus.bench.cli

import cats.implicits.*
import cats.data.*
import cats.effect.*
import com.monovore.decline.Opts
import com.monovore.decline.Command
import com.monovore.decline.Help

import scala.deriving.Mirror
import scala.Tuple
import ch.epfl.bluebrain.nexus.bench.{BuildInfo, Err, Terminal}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

object Cli extends CliOpts:

  def command: Command[Intent] =
    Command(BuildInfo.cliName, s"Nexus Benchmark (version ${BuildInfo.version})", helpFlag = true) {
      inject orElse prepareTestData orElse executeTest
    }

  private val inject =
    Opts.subcommand[Intent.Inject]("inject", "Inject data into the target system") {
      (
        endpoint,
        token.orNone.withDefault(None),
        org.withDefault("org"),
        concurrency.withDefault(10),
        startIndex.withDefault(1),
        resourceCount.withDefault(1000),
        projectCount.withDefault(10),
        ipAddresses.orNone
      )
        .mapN[Intent.Inject](Intent.Inject.apply)
        .mapValidated { inject =>
          val startLowerThanCount   = Validated.condNel(
            inject.startIdx < inject.resourceCount,
            inject,
            "The start index must be lower than the total number of resources."
          )
          val projectLowerThanCount = Validated.condNel(
            inject.projectCount < inject.resourceCount,
            inject,
            "The number of projects must be lower than the number of resources."
          )
          (startLowerThanCount, projectLowerThanCount).mapN((_, _) => inject)
        }
    }

  private val prepareTestData =
    Opts.subcommand[Intent.PrepareTestData]("prepare", "Prepare the test organization, projects and schemas") {
      (
        endpoint,
        token.orNone.withDefault(None),
        org.withDefault("org")
      ).mapN[Intent.PrepareTestData](Intent.PrepareTestData.apply)
    }

  private val executeTest =
    Opts.subcommand[Intent.Test]("test", "Execute a test") {
      (
        endpoint,
        token.orNone.withDefault(None),
        users,
        org.withDefault("org"),
        proj.withDefault("proj"),
        test,
        maxResourceIdx.withDefault(1000000),
        duration.withDefault(FiniteDuration(1L, TimeUnit.MINUTES)),
        ipAddresses.orNone
      ).mapN[Intent.Test](Intent.Test.apply)
    }

  def printHelp(terminal: Terminal, help: Help): IO[Unit] =
    if (help.errors.isEmpty) terminal.write(help.toString)
    else IO.raiseError(Err.CliErr(help))
