import build.BuildKeys._
import build.Dependencies

lazy val `snailgun-core` = project
  .in(file("core"))
  .settings(testSuiteSettings)
  .settings(
    fork in run in Compile := true,
    fork in run in Test := false,
    fork in test in Test := true,
    libraryDependencies ++= Seq(
      Dependencies.jna,
      Dependencies.jnaPlatform
    )
  )

lazy val `snailgun-cli` = project
  .in(file("cli"))
  .dependsOn(`snailgun-core`)
  .enablePlugins(GraalVMNativeImagePlugin)
  .settings(testSuiteSettings)
  .settings(
    fork in run in Compile := true,
    fork in test in Test := true,
    libraryDependencies ++= List(Dependencies.scopt),
    graalVMNativeImageOptions ++= List(
      "--no-fallback",
      "-H:+ReportExceptionStackTraces",
      // Required by GraalVM Native Image, otherwise error
      "--initialize-at-build-time=scala.Function1"
    )
  )

lazy val snailgun = project
  .in(file("."))
  .aggregate(`snailgun-core`, `snailgun-cli`)
  .settings(
    releaseEarly := { () },
    skip in publish := true
  )
