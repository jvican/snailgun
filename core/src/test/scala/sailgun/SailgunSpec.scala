package sailgun

import sailgun.protocol.Streams
import sailgun.logging.RecordingLogger
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.io.PipedOutputStream
import java.io.PipedInputStream
import java.io.PrintStream
import java.util.concurrent.TimeUnit
import monix.execution.Cancelable
import scala.concurrent.duration.FiniteDuration

object SailgunSpec extends SailgunBaseSuite {
  testSailgun("hello world works") { inputs =>
    val code = inputs.run("hello-world", new Array(0))
    assert(code == 0)
    assertNoDiff(
      inputs.output,
      "Hello, world!"
    )
  }

  testSailgun("heartbeat works") { inputs =>
    val args = Array("2000")
    val code = inputs.run("heartbeat", args)
    assert(code == 0)
    // Compute how many 'H's we should expect
    val counterForH =
      inputs.logger.getMessagesAt(Some("debug")).count(_.contains("Got client heartbeat"))
    assert(counterForH > 0)
    assertNoDiff(
      inputs.output,
      List.fill(counterForH)("H").mkString
    )
  }

  testSailgun("coursier echo works") { inputs =>
    val code = inputs.run("sailgun.utils.SailgunArgEcho", Array("foo"))
    assert(code == 0)
    assertNoDiff(
      inputs.output,
      "foo"
    )
  }

  val echoStdout = new PipedOutputStream()
  val echoStdin = new PipedInputStream(echoStdout)
  testSailgun("echo works (via stdin)", echoStdin) { inputs =>
    val ps = new PrintStream(echoStdout)

    testScheduler.scheduleOnce(FiniteDuration(500, TimeUnit.MILLISECONDS)) {
      ps.println("Hello, world!")
      Thread.sleep(10)
      ps.println("I am echo")
      Thread.sleep(10)
      ps.println("exit")
    }

    val code = inputs.run("echo", new Array(0))
    assert(code == 0)
    assertNoDiff(
      inputs.output,
      "Hello, world!I am echo"
    )
  }

  val echoStdout2 = new PipedOutputStream()
  val echoStdin2 = new PipedInputStream(echoStdout2)
  testSailgun("cancellation of echo works", echoStdin2) { inputs =>
    testScheduler.scheduleOnce(FiniteDuration(300, TimeUnit.MILLISECONDS)) {
      inputs.stop.set(true)
    }

    // Ignore return code, all we care is that we return
    inputs.run("echo", new Array(0))
    assertNoDiff(
      inputs.output,
      ""
    )
  }
}
