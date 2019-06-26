package build

import java.io.File

import bintray.BintrayKeys
import ch.epfl.scala.sbt.release.Feedback
import com.typesafe.sbt.SbtPgp.{autoImport => Pgp}
import sbt.{AutoPlugin, Def, Keys, PluginTrigger, Plugins, State, Task, ThisBuild}
import sbt.io.IO
import sbt.io.syntax.fileToRichFile
import sbt.librarymanagement.syntax.stringToOrganization
import sbtdynver.GitDescribeOutput
import ch.epfl.scala.sbt.release.ReleaseEarlyPlugin.{autoImport => ReleaseEarlyKeys}

object BuildPlugin extends AutoPlugin {
  import sbt.plugins.JvmPlugin
  import sbt.plugins.IvyPlugin
  import com.typesafe.sbt.SbtPgp
  import ch.epfl.scala.sbt.release.ReleaseEarlyPlugin
  import com.lucidchart.sbt.scalafmt.ScalafmtCorePlugin

  override def trigger: PluginTrigger = allRequirements
  override def requires: Plugins =
    JvmPlugin && ScalafmtCorePlugin && ReleaseEarlyPlugin && SbtPgp && IvyPlugin
  val autoImport = BuildKeys

  override def globalSettings: Seq[Def.Setting[_]] =
    BuildImplementation.globalSettings
  override def buildSettings: Seq[Def.Setting[_]] =
    BuildImplementation.buildSettings
  override def projectSettings: Seq[Def.Setting[_]] =
    BuildImplementation.projectSettings
}

object BuildKeys {
  import sbt.{Reference, RootProject, ProjectRef, BuildRef, file}

  def inProject(ref: Reference)(ss: Seq[Def.Setting[_]]): Seq[Def.Setting[_]] =
    sbt.inScope(sbt.ThisScope.in(project = ref))(ss)

  def inProjectRefs(refs: Seq[Reference])(ss: Def.Setting[_]*): Seq[Def.Setting[_]] =
    refs.flatMap(inProject(_)(ss))

  def inCompileAndTest(ss: Def.Setting[_]*): Seq[Def.Setting[_]] =
    Seq(sbt.Compile, sbt.Test).flatMap(sbt.inConfig(_)(ss))

  import sbt.Test
  val testSuiteSettings: Seq[Def.Setting[_]] = List(
    Keys.testFrameworks += new sbt.TestFramework("utest.runner.Framework"),
    Keys.libraryDependencies ++= List(
      Dependencies.monix % Test,
      Dependencies.utest % Test,
      Dependencies.pprint % Test,
      Dependencies.nailgun % Test,
      Dependencies.difflib % Test,
      Dependencies.nailgunExamples % Test,
    )
  )

}

object BuildImplementation {
  import sbt.{url, file}
  import sbt.{Developer, Resolver, Watched, Compile, Test}
  import sbtdynver.DynVerPlugin.{autoImport => DynVerKeys}

  def GitHub(org: String, project: String): java.net.URL =
    url(s"https://github.com/$org/$project")
  def GitHubDev(handle: String, fullName: String, email: String) =
    Developer(handle, fullName, email, url(s"https://github.com/$handle"))

  final val globalSettings: Seq[Def.Setting[_]] = Seq(
    Keys.cancelable := true,
    Keys.testOptions in Test += sbt.Tests.Argument("-oD"),
    Keys.publishArtifact in Test := false,
    Pgp.pgpPublicRing := {
      if (Keys.insideCI.value) file("/drone/.gnupg/pubring.asc")
      else Pgp.pgpPublicRing.value
    },
    Pgp.pgpSecretRing := {
      if (Keys.insideCI.value) file("/drone/.gnupg/secring.asc")
      else Pgp.pgpSecretRing.value
    }
  )

  private final val ThisRepo = GitHub("jvican", "sailgun")
  final val buildSettings: Seq[Def.Setting[_]] = Seq(
    Keys.organization := "me.vican.jorge",
    Keys.updateOptions := Keys.updateOptions.value.withCachedResolution(true),
    Keys.scalaVersion := Dependencies.Scala212Version,
    Keys.triggeredMessage := Watched.clearWhenTriggered,
    Keys.resolvers := {
      val oldResolvers = Keys.resolvers.value
      val scalacenterResolver = Resolver.bintrayRepo("jvican", "releases")
      (oldResolvers :+ scalacenterResolver).distinct
    },
    ReleaseEarlyKeys.releaseEarlyWith := {
      // Only tag releases go directly to Maven Central, the rest go to bintray!
      val isOnlyTag = DynVerKeys.dynverGitDescribeOutput.value
        .map(v => v.commitSuffix.isEmpty && v.dirtySuffix.value.isEmpty)
      if (isOnlyTag.getOrElse(false)) ReleaseEarlyKeys.SonatypePublisher
      else ReleaseEarlyKeys.BintrayPublisher
    },
    BintrayKeys.bintrayOrganization := Some("jvican"),
    Keys.startYear := Some(2019),
    Keys.autoAPIMappings := true,
    Keys.publishMavenStyle := true,
    Keys.homepage := Some(ThisRepo),
    Keys.licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    Keys.developers := List(
      GitHubDev("jvican", "Jorge Vicente Cantero", "jorge@vican.me")
    )
  )

  import sbt.{CrossVersion, compilerPlugin}
  final val projectSettings: Seq[Def.Setting[_]] = Seq(
    BintrayKeys.bintrayPackage := "sailgun",
    BintrayKeys.bintrayRepository := "releases",
    // Add some metadata that is useful to see in every on-merge bintray release
    BintrayKeys.bintrayPackageLabels := List("client", "nailgun", "server", "scala", "tooling"),
    ReleaseEarlyKeys.releaseEarlyPublish := BuildDefaults.releaseEarlyPublish.value,
    Keys.scalacOptions := reasonableCompileOptions,
    // Legal requirement: license and notice files must be in the published jar
    Keys.resources in Compile ++= BuildDefaults.getLicense.value,
    Keys.publishArtifact in Test := false,
    Keys.publishArtifact in (Compile, Keys.packageDoc) := {
      val output = DynVerKeys.dynverGitDescribeOutput.value
      val version = Keys.version.value
      BuildDefaults.publishDocAndSourceArtifact(output, version)
    },
    Keys.publishArtifact in (Compile, Keys.packageSrc) := {
      val output = DynVerKeys.dynverGitDescribeOutput.value
      val version = Keys.version.value
      BuildDefaults.publishDocAndSourceArtifact(output, version)
    },
    Keys.publishLocalConfiguration in Compile :=
      Keys.publishLocalConfiguration.value.withOverwrite(true)
  )

  final val reasonableCompileOptions = (
    "-deprecation" :: "-encoding" :: "UTF-8" :: "-feature" :: "-language:existentials" ::
      "-language:higherKinds" :: "-language:implicitConversions" :: "-unchecked" :: "-Yno-adapted-args" ::
      "-Ywarn-numeric-widen" :: "-Ywarn-value-discard" :: "-Xfuture" :: Nil
  )

  object BuildDefaults {
    val releaseEarlyPublish: Def.Initialize[Task[Unit]] = Def.task {
      val logger = Keys.streams.value.log
      val name = Keys.name.value
      // We force publishSigned for all of the modules, yes or yes.
      if (ReleaseEarlyKeys.releaseEarlyWith.value == ReleaseEarlyKeys.SonatypePublisher) {
        logger.info(Feedback.logReleaseSonatype(name))
      } else {
        logger.info(Feedback.logReleaseBintray(name))
      }

      Pgp.PgpKeys.publishSigned.value
    }

    // From sbt-sensible https://gitlab.com/fommil/sbt-sensible/issues/5, legal requirement
    val getLicense: Def.Initialize[Task[Seq[File]]] = Def.task {
      val orig = (Keys.resources in Compile).value
      val base = Keys.baseDirectory.value
      val root = (Keys.baseDirectory in ThisBuild).value

      def fileWithFallback(name: String): File =
        if ((base / name).exists) base / name
        else if ((root / name).exists) root / name
        else throw new IllegalArgumentException(s"legal file $name must exist")

      Seq(fileWithFallback("LICENSE.md"), fileWithFallback("NOTICE.md"))
    }

    /**
     * This setting figures out whether the version is a snapshot or not and configures
     * the source and doc artifacts that are published by the build.
     *
     * Snapshot is a term with no clear definition. In this code, a snapshot is a revision
     * that is dirty, e.g. has time metadata in its representation. In those cases, the
     * build will not publish doc and source artifacts by any of the publishing actions.
     */
    def publishDocAndSourceArtifact(info: Option[GitDescribeOutput], version: String): Boolean = {
      val isStable = info.map(_.dirtySuffix.value.isEmpty)
      !isStable.exists(stable => !stable || version.endsWith("-SNAPSHOT"))
    }
  }
}
