/*
scalafmt: {
  style = defaultWithAlign
  maxColumn = 150
  version = 2.0.0
  align.tokens = [
    { code = "=>", owner = "Case" }
    { code = "?", owner = "Case" }
    { code = "extends", owner = "Defn.(Class|Trait|Object)" }
    { code = "//", owner = ".*" }
    { code = "{", owner = "Template" }
    { code = "}", owner = "Template" }
    { code = ":=", owner = "Term.ApplyInfix" }
    { code = "++=", owner = "Term.ApplyInfix" }
    { code = "+=", owner = "Term.ApplyInfix" }
    { code = "%", owner = "Term.ApplyInfix" }
    { code = "%%", owner = "Term.ApplyInfix" }
    { code = "%%%", owner = "Term.ApplyInfix" }
    { code = "->", owner = "Term.ApplyInfix" }
    { code = "?", owner = "Term.ApplyInfix" }
    { code = "<-", owner = "Enumerator.Generator" }
    { code = "?", owner = "Enumerator.Generator" }
    { code = "=", owner = "(Enumerator.Val|Defn.(Va(l|r)|Def|Type))" }
  ]
}
 */

val catsVersion          = "2.0.0"
val catsEffectVersion    = "2.0.0"
val circeVersion         = "0.12.1"
val declineVersion       = "1.0.0"
val fs2Version           = "1.0.5"
val gatlingVersion       = "3.2.1"
val http4sVersion        = "0.21.0-M5"
val kindProjectorVersion = "0.10.3"
val logbackVersion       = "1.2.3"
val monixVersion         = "3.0.0"
val pureconfigVersion    = "0.12.0"

lazy val catsCore      = "org.typelevel"         %% "cats-core"                % catsVersion
lazy val catsEffect    = "org.typelevel"         %% "cats-effect"              % catsEffectVersion
lazy val circeCore     = "io.circe"              %% "circe-core"               % circeVersion
lazy val circeGeneric  = "io.circe"              %% "circe-generic"            % circeVersion
lazy val circeLiteral  = "io.circe"              %% "circe-literal"            % circeVersion
lazy val circeParser   = "io.circe"              %% "circe-parser"             % circeVersion
lazy val decline       = "com.monovore"          %% "decline"                  % declineVersion
lazy val gatlingCharts = "io.gatling"            % "gatling-charts"            % gatlingVersion
lazy val gatlingCore   = "io.gatling"            % "gatling-core"              % gatlingVersion
lazy val gatlingHC     = "io.gatling.highcharts" % "gatling-charts-highcharts" % gatlingVersion
lazy val gatlingHttp   = "io.gatling"            % "gatling-http"              % gatlingVersion
lazy val gatlingTF     = "io.gatling"            % "gatling-test-framework"    % gatlingVersion
lazy val http4sCirce   = "org.http4s"            %% "http4s-circe"             % http4sVersion
lazy val http4sClient  = "org.http4s"            %% "http4s-blaze-client"      % http4sVersion
lazy val kindProjector = "org.typelevel"         %% "kind-projector"           % kindProjectorVersion
lazy val logback       = "ch.qos.logback"        % "logback-classic"           % logbackVersion
lazy val monixEval     = "io.monix"              %% "monix-eval"               % monixVersion
lazy val monixTail     = "io.monix"              %% "monix-tail"               % monixVersion
lazy val pureconfig    = "com.github.pureconfig" %% "pureconfig"               % pureconfigVersion

lazy val benchmarks = project
  .in(file("."))
  .enablePlugins(JavaAppPackaging)
  .settings(addCompilerPlugin(kindProjector))
  .settings(
    name           := "nexus-benchmarks",
    organization   := "ch.epfl.bluebrain.nexus",
    scalaVersion   := "2.12.10",
    publish / skip := true,
    run / fork     := true,
    scalacOptions --= Seq("-Xfatal-warnings"),
    libraryDependencies := Seq(
      catsCore,
      circeCore,
      circeGeneric,
      circeLiteral,
      circeParser,
      decline,
      gatlingCharts,
      gatlingCore,
      gatlingHC,
      gatlingHttp,
      gatlingTF,
      http4sCirce,
      http4sClient,
      logback,
      monixEval,
      pureconfig
    )
  )
