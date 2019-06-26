val `sailgun-build` = project
  .in(file("."))
  .settings(
    scalaVersion := "2.12.8",
    addSbtPlugin("com.dwijnand" % "sbt-dynver" % "3.1.0"),
    addSbtPlugin("com.lucidchart" % "sbt-scalafmt" % "1.14"),
    addSbtPlugin("ch.epfl.scala" % "sbt-release-early" % "2.1.1"),
    addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.1.0-M13-2"),
    addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.22")
  )
