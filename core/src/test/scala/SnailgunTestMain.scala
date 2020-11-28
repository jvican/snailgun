package snailgun

import snailgun.logging.RecordingLogger
import java.util.concurrent.atomic.AtomicBoolean
import java.io.ByteArrayOutputStream
import snailgun.protocol.Streams
import java.nio.channels.Pipe
import java.nio.channels.Channels
import java.io.PrintStream
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import java.nio.file.Files
import java.nio.file.Paths

object SnailgunTestMain extends SnailgunBaseSuite {
  def main(args: Array[String]): Unit = {
    val logger = new RecordingLogger()
    val stop = new AtomicBoolean(false)
    val fileOut = new PrintStream(Files.newOutputStream(Paths.get("snailgun-test.logs")))
    try {
      val pipe = Pipe.open()
      val in = Channels.newInputStream(pipe.source())
      val outToClient = new PrintStream(Channels.newOutputStream(pipe.sink()))

      val testScheduler = monix.execution.Scheduler.io()

      val out = new ByteArrayOutputStream()
      val streams = Streams(in, out, out)
      withRunningServer(streams, logger) { client =>
        val inputs = TestInputs(streams, logger, stop, client, out)

        testScheduler.scheduleOnce(FiniteDuration(1500, TimeUnit.MILLISECONDS)) {
          Thread.sleep(500)
          outToClient.println("Hello, world!")
          fileOut.println("Waiting for 20s...")
          Thread.sleep(10000)
          outToClient.println("WHAAAAAAAAAAAAAAAAAAAt!")
          /*
          Thread.sleep(5000)
          outToClient.println("2222222222222222!")
          Thread.sleep(5000)
          outToClient.println("hihihi!")
           */
          Thread.sleep(50)
          fileOut.println("Exiting!")
          try outToClient.println("exit")
          catch {
            case t: Throwable => t.printStackTrace()
          }
          //logger.info("DONE!")
        }

        val code = inputs.run("echo", new Array(0), interactive = false)
        fileOut.println("FINISHED!")
        fileOut.println(code)
        fileOut.println(inputs.output)
        ()
      }
    } finally {
      logger.dump(out = fileOut)
    }
  }
}
