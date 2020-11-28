import build.BuildKeys._
import build.Dependencies

lazy val `snailgun-core` = project
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
      "--no-server",
      "--no-fallback",
      // Required by GraalVM Native Image, otherwise error
      "--allow-incomplete-classpath",
      "--enable-all-security-services",
      "-H:+PrintClassInitialization",
      "-H:+ReportExceptionStackTraces",
      "--report-unsupported-elements-at-runtime",
      "--initialize-at-build-time=scala.Symbol,scala.Function1,scala.Function2,scala.runtime.StructuralCallSite,scala.runtime.EmptyMethodCache"
    )
  )

lazy val snailgun = project
  .in(file("."))
  .aggregate(`snailgun-core`, `snailgun-cli`)
  .settings(
    releaseEarly := { () },
    skip in publish := true
  )
