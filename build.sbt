ThisBuild / scalaVersion := "3.3.3"

lazy val deployAll = taskKey[Seq[File]](
  "Build the production bundle and collect every deployable file"
)

lazy val root = project.in(file("."))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "bus-homepage",
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies ++= Seq(
      "com.raquo" %%% "laminar" % "17.0.0",
      "io.github.cquiroz" %%% "scala-java-time" % "2.6.0"
    ),
    deployAll := {
      // A deployment only needs the optimized linker output. Depending directly
      // on this scoped task prevents sbt from also running the fast linker.
      val linkerOutput = (Compile / fullLinkJSOutput).value
      val output = target.value / "deploy"
      val rootDirectory = baseDirectory.value

      IO.delete(output)
      IO.createDirectory(output)
      val applicationBundle = linkerOutput / "main.js"
      if (!applicationBundle.isFile)
        sys.error("Scala.js did not produce an application bundle")
      IO.copyFile(applicationBundle, output / "app.js")
      IO.copyFile(rootDirectory / "index.html", output / "index.html")
      IO.copyFile(rootDirectory / "style.css", output / "style.css")
      IO.copyDirectory(rootDirectory / "config", output / "config")

      (output ** "*").get.filter(_.isFile)
    }
  )
