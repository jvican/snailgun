import build.BuildKeys._
import build.Dependencies

lazy val sailgun = project
  .in(file("."))
  .enablePlugins(GraalVMNativeImagePlugin)
  .settings(testSuiteSettings)
  .settings(
    name := "sailgun",
    fork in run in Compile := true,
    libraryDependencies ++= Seq(
      Dependencies.jna,
      Dependencies.jnaPlatform
    ),
    graalVMNativeImageOptions ++= List(
      "--no-fallback",
      "-H:+ReportExceptionStackTraces",
      "-H:Log=registerResource",
      "-H:IncludeResources=com/sun/jna/darwin/libjnidispatch.jnilib"
    )
  )
