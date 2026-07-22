ThisBuild / scalaVersion := "3.3.3"

lazy val root = project.in(file("."))
  // ScalaJSBundlerPlugin provides the webpack task and installs npmDependencies.
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .settings(
    name := "bus-homepage",
    scalaJSUseMainModuleInitializer := true,
    webpack / version := "5.91.0",
    webpackConfigFile := Some(baseDirectory.value / "webpack.config.js"),
    libraryDependencies ++= Seq(
      "com.raquo" %%% "laminar" % "17.0.0",
      "io.github.cquiroz" %%% "scala-java-time" % "2.6.0"
    ),
    Compile / npmDependencies ++= Seq(
      "hafas-client" -> "6.0.0",
      // hafas-client still imports Node core modules when bundled for a browser.
      "assert" -> "2.1.0",
      "buffer" -> "6.0.3",
      "crypto-browserify" -> "3.12.1",
      "https-browserify" -> "1.0.0",
      "process" -> "0.11.10",
      "stream-browserify" -> "3.0.0",
      "stream-http" -> "3.2.0",
      // webpack's AJV dependency is not installed transitively by npm 11.
      "uri-js" -> "4.4.1"
    )
  )
