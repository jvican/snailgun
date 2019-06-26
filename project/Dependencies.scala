package build

object Dependencies {
  import sbt.librarymanagement.syntax.stringToOrganization
  val Scala212Version = "2.12.8"
  val jnaVersion = "4.5.0"
  val nailgunVersion = "ee3c4343"
  val difflibVersion = "1.3.0"

  val monix = "io.monix" %% "monix" % "2.3.3"
  val utest = "com.lihaoyi" %% "utest" % "0.6.6"
  val pprint = "com.lihaoyi" %% "pprint" % "0.5.3"
  val jna = "net.java.dev.jna" % "jna" % jnaVersion
  val slf4jApi = "org.slf4j" % "slf4j-api" % "1.7.26"
  val jnaPlatform = "net.java.dev.jna" % "jna-platform" % jnaVersion
  val nailgun = "ch.epfl.scala" % "nailgun-server" % nailgunVersion
  val nailgunExamples = "ch.epfl.scala" % "nailgun-examples" % nailgunVersion
  val difflib = "com.googlecode.java-diff-utils" % "diffutils" % difflibVersion
}
