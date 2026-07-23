ThisBuild / scalaVersion := "3.3.3"

lazy val root = project.in(file("."))
  // ScalaJSBundlerPlugin provides the webpack task and installs npmDependencies.
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .settings(
    name := "bus-homepage",
    scalaJSUseMainModuleInitializer := true,
    Compile / webpack / version := "5.91.0",
    webpackConfigFile := Some(baseDirectory.value / "webpack.config.js"),
    libraryDependencies ++= Seq(
      "com.raquo" %%% "laminar" % "17.0.0",
      "io.github.cquiroz" %%% "scala-java-time" % "2.6.0"
    ),
    Compile / npmDependencies += "uri-js" -> "4.4.1"
  )
