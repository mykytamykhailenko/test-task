scalaVersion := "2.13.11"

val circeVersion = "0.14.1"

lazy val targetFinder = (project in file("target-finder"))
  .settings(
    name := "target-finder",
    version := "latest",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % "0.14.5",
      "io.circe" %% "circe-generic" % "0.14.5",
      "io.circe" %% "circe-parser" % "0.14.5",
      "dev.zio" %% "zio" % "2.0.15",
      "dev.zio" %% "zio-http" % "3.0.0-RC2"
    ),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    dockerExposedPorts += 8080
  )
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)

lazy val targetRouter = (project in file("target-router"))
  .dependsOn(targetFinder)
  .settings(
    name := "target-router",
    version := "latest",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-config" % "3.0.7",
      "dev.zio" %% "zio-config-typesafe" % "3.0.7",
      "dev.zio" %% "zio-config-magnolia" % "3.0.7"),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    dockerExposedPorts += 4040
  )
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)




