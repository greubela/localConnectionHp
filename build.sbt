ThisBuild / scalaVersion := "3.3.3"

lazy val root = project.in(file("."))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "bus-homepage",
    scalaJSUseMainModuleInitializer := true,
    webpack / version := "5.91.0",
    libraryDependencies ++= Seq(
      "com.raquo" %%% "laminar" % "17.0.0"
    ),
    npmDependencies in Compile += "hafas-client" -> "6.0.0"
  )
