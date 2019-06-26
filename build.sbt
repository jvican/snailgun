import build.BuildKeys._
import build.Dependencies

lazy val `sailgun-core` = project
  .in(file("core"))
  .settings(testSuiteSettings)
  .settings(
    fork in run in Compile := true,
    fork in test in Test := true,
    libraryDependencies ++= Seq(
      Dependencies.jna,
      Dependencies.jnaPlatform
    )
  )

lazy val `sailgun-cli` = project
  .in(file("cli"))
  .dependsOn(`sailgun-core`)
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

lazy val sailgun = project
  .in(file("."))
  .aggregate(`sailgun-core`, `sailgun-cli`)
  .settings(
    releaseEarly := { () },
    skip in publish := true
  )
