ThisBuild / scalaVersion := "3.3.3"

lazy val root = project.in(file("."))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "bus-homepage",
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "2.8.0"
  )
