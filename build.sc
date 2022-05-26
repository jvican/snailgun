import mill._
import mill.scalalib._
import mill.scalalib.publish._

import $ivy.`io.get-coursier::coursier-launcher:2.1.0-M2`

import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.1.4`
import de.tobiasroeser.mill.vcs.version._

import $ivy.`io.github.alexarchambault.mill::mill-native-image::0.1.19`
import io.github.alexarchambault.millnativeimage.NativeImage

import $ivy.`io.github.alexarchambault.mill::mill-native-image-upload:0.1.19`
import io.github.alexarchambault.millnativeimage.upload.Upload

import scala.concurrent.duration._
import scala.util.Properties

def scalaVersions = Seq("2.12.15", "2.13.8")
def mainScalaVersion = scalaVersions.last

object core extends Cross[Core](scalaVersions: _*)
object cli extends Cross[Cli](scalaVersions: _*)

class Core(val crossScalaVersion: String) extends CrossSbtModule with SnailgunPublishModule {
  def ivyDeps = super.ivyDeps() ++ Seq(
    ivy"net.java.dev.jna:jna-platform:5.6.0"
  )
  object test extends Tests {
    def testFramework = "utest.runner.Framework"
    def ivyDeps = super.ivyDeps() ++ Seq(
      ivy"com.lihaoyi::utest:0.7.2",
      ivy"com.lihaoyi::pprint:0.6.0",
      ivy"com.googlecode.java-diff-utils:diffutils:1.3.0",
      ivy"io.monix::monix:3.3.0",
      ivy"ch.epfl.scala:nailgun-server:ee3c4343"
    )
  }
}

class Cli(val crossScalaVersion: String)
    extends CrossSbtModule
    with NativeImage
    with SnailgunPublishModule {
  def moduleDeps = Seq(
    core()
  )
  def ivyDeps = super.ivyDeps() ++ Seq(
    ivy"com.github.scopt::scopt:4.0.0-RC2"
  )
  def mainClass = Some("snailgun.Cli")

  def nativeImageClassPath = runClasspath()
  def nativeImageMainClass = mainClass().getOrElse {
    sys.error("No main class found")
  }
  def nativeImageGraalVmJvmId = "graalvm-java17:22.1.0"

  def transitiveJars: T[Agg[PathRef]] = {

    def allModuleDeps(todo: List[JavaModule]): List[JavaModule] =
      todo match {
        case Nil => Nil
        case h :: t =>
          h :: allModuleDeps(h.moduleDeps.toList ::: t)
      }

    T {
      mill.define.Target.traverse(allModuleDeps(this :: Nil).distinct)(m => T.task(m.jar()))()
    }
  }

  def jarClassPath = T {
    val cp = runClasspath() ++ transitiveJars()
    cp.filter(ref => os.exists(ref.path) && !os.isDir(ref.path))
  }

  def standaloneLauncher = T {

    val cachePath = os.Path(coursier.cache.FileCache().location, os.pwd)
    def urlOf(path: os.Path): Option[String] =
      if (path.startsWith(cachePath)) {
        val segments = path.relativeTo(cachePath).segments
        val url = segments.head + "://" + segments.tail.mkString("/")
        Some(url)
      } else None

    import coursier.launcher.{
      AssemblyGenerator,
      BootstrapGenerator,
      ClassPathEntry,
      Parameters,
      Preamble
    }
    import scala.util.Properties.isWin
    val cp = jarClassPath().map(_.path)
    val mainClass0 = mainClass().getOrElse(sys.error("No main class"))

    val dest = T.ctx().dest / (if (isWin) "launcher.bat" else "launcher")

    val preamble = Preamble()
      .withOsKind(isWin)
      .callsItself(isWin)
    val entries = cp.map { path =>
      urlOf(path) match {
        case None =>
          val content = os.read.bytes(path)
          val name = path.last
          ClassPathEntry.Resource(name, os.mtime(path), content)
        case Some(url) => ClassPathEntry.Url(url)
      }
    }
    val loaderContent = coursier.launcher.ClassLoaderContent(entries)
    val params = Parameters
      .Bootstrap(Seq(loaderContent), mainClass0)
      .withDeterministic(true)
      .withPreamble(preamble)

    BootstrapGenerator.generate(params, dest.toNIO)

    PathRef(dest)
  }
}

def ghOrg = "jvican"
def ghName = "snailgun"
trait SnailgunPublishModule extends PublishModule {
  import mill.scalalib.publish._
  def artifactName = "snailgun-" + super.artifactName()
  def pomSettings = PomSettings(
    description = artifactName(),
    organization = "me.vican.jorge",
    url = s"https://github.com/$ghOrg/$ghName",
    licenses = Seq(License.`Apache-2.0`),
    versionControl = VersionControl.github(ghOrg, ghName),
    developers = Seq(
      Developer(
        "jvican",
        "Jorge Vicente Cantero",
        "https://github.com/jvican",
        Some("jorge@vican.me")
      )
    )
  )
  def publishVersion =
    finalPublishVersion()
}

private def computePublishVersion(state: VcsState, simple: Boolean): String =
  if (state.commitsSinceLastTag > 0)
    if (simple) {
      val versionOrEmpty = state.lastTag
        .filter(_ != "latest")
        .filter(_ != "nightly")
        .map(_.stripPrefix("v"))
        .flatMap { tag =>
          if (simple) {
            val idx = tag.lastIndexOf(".")
            if (idx >= 0)
              Some(tag.take(idx + 1) + (tag.drop(idx + 1).toInt + 1).toString + "-SNAPSHOT")
            else
              None
          } else {
            val idx = tag.indexOf("-")
            if (idx >= 0) Some(tag.take(idx) + "+" + tag.drop(idx + 1) + "-SNAPSHOT")
            else None
          }
        }
        .getOrElse("0.0.1-SNAPSHOT")
      Some(versionOrEmpty)
        .filter(_.nonEmpty)
        .getOrElse(state.format())
    } else {
      val rawVersion = os
        .proc("git", "describe", "--tags")
        .call()
        .out
        .text()
        .trim
        .stripPrefix("v")
        .replace("latest", "0.0.0")
        .replace("nightly", "0.0.0")
      val idx = rawVersion.indexOf("-")
      if (idx >= 0) rawVersion.take(idx) + "-" + rawVersion.drop(idx + 1) + "-SNAPSHOT"
      else rawVersion
    }
  else
    state.lastTag
      .getOrElse(state.format())
      .stripPrefix("v")

private def finalPublishVersion = {
  val isCI = System.getenv("CI") != null
  if (isCI)
    T.persistent {
      val state = VcsVersion.vcsState()
      computePublishVersion(state, simple = false)
    }
  else
    T {
      val state = VcsVersion.vcsState()
      computePublishVersion(state, simple = true)
    }
}

def nativeImage = T {
  cli(mainScalaVersion).nativeImage()
}

object ci extends Module {
  def publishSonatype(tasks: mill.main.Tasks[PublishModule.PublishData]) = T.command {
    publishSonatype0(
      data = define.Target.sequence(tasks.value)(),
      log = T.ctx().log
    )
  }

  private def publishSonatype0(
      data: Seq[PublishModule.PublishData],
      log: mill.api.Logger
  ): Unit = {

    val credentials = sys.env("SONATYPE_USERNAME") + ":" + sys.env("SONATYPE_PASSWORD")
    val pgpPassword = sys.env("PGP_PASSWORD")
    val timeout = 10.minutes

    val artifacts = data.map { case PublishModule.PublishData(a, s) =>
      (s.map { case (p, f) => (p.path, f) }, a)
    }

    val isRelease = {
      val versions = artifacts.map(_._2.version).toSet
      val set = versions.map(!_.endsWith("-SNAPSHOT"))
      assert(
        set.size == 1,
        s"Found both snapshot and non-snapshot versions: ${versions.toVector.sorted.mkString(", ")}"
      )
      set.head
    }
    val publisher = new scalalib.publish.SonatypePublisher(
      uri = "https://s01.oss.sonatype.org/service/local",
      snapshotUri = "https://s01.oss.sonatype.org/content/repositories/snapshots",
      credentials = credentials,
      signed = true,
      // format: off
      gpgArgs = Seq(
        "--detach-sign",
        "--batch=true",
        "--yes",
        "--pinentry-mode", "loopback",
        "--passphrase", pgpPassword,
        "--armor",
        "--use-agent"
      ),
      // format: on
      readTimeout = timeout.toMillis.toInt,
      connectTimeout = timeout.toMillis.toInt,
      log = log,
      awaitTimeout = timeout.toMillis.toInt,
      stagingRelease = isRelease
    )

    publisher.publishAll(isRelease, artifacts: _*)
  }

  def copyLauncher(directory: String = "artifacts") = T.command {
    val nativeLauncher = cli(mainScalaVersion).nativeImage().path
    Upload.copyLauncher(
      nativeLauncher,
      directory,
      "snailgun",
      compress = true
    )
  }

  def copyJvmLauncher(directory: String = "artifacts") = T.command {
    val platformExecutableJarExtension = if (Properties.isWin) ".bat" else ""
    val launcher = cli(mainScalaVersion).standaloneLauncher().path
    os.copy(
      launcher,
      os.Path(directory, os.pwd) / s"snailgun$platformExecutableJarExtension",
      createFolders = true,
      replaceExisting = true
    )
  }

  private def ghToken(): String = Option(System.getenv("UPLOAD_GH_TOKEN")).getOrElse {
    sys.error("UPLOAD_GH_TOKEN not set")
  }
  def uploadLaunchers(directory: String = "artifacts") = T.command {
    val version = cli(mainScalaVersion).publishVersion()

    val path = os.Path(directory, os.pwd)
    val launchers = os.list(path).filter(os.isFile(_)).map { path =>
      path.toNIO -> path.last
    }
    val (tag, overwriteAssets) =
      if (version.endsWith("-SNAPSHOT")) ("nightly", true)
      else ("v" + version, false)
    System.err.println(s"Uploading to tag $tag (overwrite assets: $overwriteAssets)")
    Upload.upload(ghOrg, ghName, ghToken(), tag, dryRun = false, overwrite = overwriteAssets)(
      launchers: _*
    )
  }
}
