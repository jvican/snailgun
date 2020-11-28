package build

object Dependencies {
  import sbt.librarymanagement.syntax.stringToOrganization
  val Scala212Version = "2.12.11"
  val Scala213Version = "2.13.4"

  val jnaVersion = "4.5.0"
  val nailgunVersion = "ee3c4343"
  val difflibVersion = "1.3.0"
  val caseAppVersion = "1.2.0-faster-compile-time"
  val shapelessVersion = "2.3.3-lower-priority-coproduct"

  val monix = "io.monix" %% "monix" % "3.3.0"
  val utest = "com.lihaoyi" %% "utest" % "0.7.2"
  val pprint = "com.lihaoyi" %% "pprint" % "0.6.0"
  val jna = "net.java.dev.jna" % "jna" % jnaVersion
  val slf4jApi = "org.slf4j" % "slf4j-api" % "1.7.26"
  val jnaPlatform = "net.java.dev.jna" % "jna-platform" % jnaVersion
  val nailgun = "ch.epfl.scala" % "nailgun-server" % nailgunVersion
  val nailgunExamples = "ch.epfl.scala" % "nailgun-examples" % nailgunVersion
  val difflib = "com.googlecode.java-diff-utils" % "diffutils" % difflibVersion

  val scopt = "com.github.scopt" %% "scopt" % "4.0.0-RC2"
}
