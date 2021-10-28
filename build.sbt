/*
scalafmt: {
  maxColumn = 150
  align.tokens.add = [
    { code = ":=", owner = "Term.ApplyInfix" }
    { code = "+=", owner = "Term.ApplyInfix" }
    { code = "++=", owner = "Term.ApplyInfix" }
    { code = "~=", owner = "Term.ApplyInfix" }
    { code = "cross", owner = "Term.ApplyInfix" }
  ]
}
 */

val catsEffectVersion = "3.2.9"
val circeVersion      = "0.15.0-M1"
val declineVersion    = "2.2.0"
val fansiVersion      = "0.2.14"
val fs2Version        = "3.1.6"
val gatlingVersion    = "3.6.1"
val http4sVersion     = "1.0.0-M29"
val jlineVersion      = "3.21.0"
val logbackVersion    = "1.2.6"
val munitVersion      = "0.7.29"

lazy val catsEffect    = "org.typelevel"        %% "cats-effect"               % catsEffectVersion
lazy val circeCore     = "io.circe"             %% "circe-core"                % circeVersion
lazy val circeParser   = "io.circe"             %% "circe-parser"              % circeVersion
lazy val decline       = "com.monovore"         %% "decline"                   % declineVersion
lazy val fansi         = "com.lihaoyi"          %% "fansi"                     % fansiVersion
lazy val fs2Core       = "co.fs2"               %% "fs2-core"                  % fs2Version
lazy val fs2IO         = "co.fs2"               %% "fs2-io"                    % fs2Version
lazy val gatlingCharts = "io.gatling"            % "gatling-charts"            % gatlingVersion
lazy val gatlingCore   = "io.gatling"            % "gatling-core"              % gatlingVersion
lazy val gatlingHC     = "io.gatling.highcharts" % "gatling-charts-highcharts" % gatlingVersion
lazy val gatlingHttp   = "io.gatling"            % "gatling-http"              % gatlingVersion
lazy val gatlingTF     = "io.gatling"            % "gatling-test-framework"    % gatlingVersion
lazy val http4sClient  = "org.http4s"           %% "http4s-blaze-client"       % http4sVersion
lazy val http4sCirce   = "org.http4s"           %% "http4s-circe"              % http4sVersion
lazy val jlineTerminal = "org.jline"             % "jline-terminal"            % jlineVersion
lazy val logback       = "ch.qos.logback"        % "logback-classic"           % logbackVersion
lazy val munit         = "org.scalameta"        %% "munit"                     % munitVersion

lazy val benchmarks = project
  .in(file("."))
  .enablePlugins(BuildInfoPlugin, JavaAppPackaging)
  .settings(
    buildInfoSettings,
    organization        := "ch.epfl.bluebrain.nexus",
    name                := "benchmarks",
    moduleName          := "benchmarks",
    scalaVersion        := "3.0.2",
    publish / skip      := true,
    libraryDependencies := Seq(
      catsEffect,
      circeCore,
      circeParser,
      decline,
      fansi,
      fs2Core,
      fs2IO,
      gatlingCharts,
      gatlingCore,
      gatlingHC,
      gatlingHttp,
      gatlingTF,
      http4sClient,
      http4sCirce,
      jlineTerminal,
      logback,
      munit % Test
    )
  )

val cliName   = SettingKey[String]("cliName", "The name of the command line utility to be used in help.")
val schemaIdx = TaskKey[Seq[String]]("schemaIdx", "A list of schema names on the classpath.")

lazy val buildInfoSettings = Seq(
  cliName          := {
    sys.env.get("CLI_NAME") match {
      case Some(value) => value
      case None        => "nexus-bench"
    }
  },
  schemaIdx        := {
    val parent = (Compile / resourceDirectory).value
    val pf     = (parent / "schemas" / "modular") * "*.json"
    pf.get().map(_.getName)
  },
  buildInfoKeys    := Seq[BuildInfoKey](version, cliName, schemaIdx),
  buildInfoPackage := "ch.epfl.bluebrain.nexus.bench"
)

ThisBuild / evictionErrorLevel := Level.Info

Global / excludeLintKeys ++= Set(
  cliName,
  schemaIdx
)
