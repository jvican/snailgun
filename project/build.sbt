val `snailgun-build` = project
  .in(file("."))
  .settings(
    scalaVersion := "2.12.11",
    addSbtPlugin("com.dwijnand" % "sbt-dynver" % "3.1.0"),
    addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.8.1"),
    addSbtPlugin("ch.epfl.scala" % "sbt-release-early" % "2.1.1+4-9d76569a"),
    addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.22")
  )
