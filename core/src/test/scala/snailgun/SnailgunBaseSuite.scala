package snailgun

import snailgun.logging.Logger
import snailgun.logging.RecordingLogger
import snailgun.logging.Slf4jAdapter
import snailgun.protocol.Defaults
import snailgun.protocol.Streams
import snailgun.utils.ExitNail
import snailgun.utils.SnailgunHeartbeat
import snailgun.utils.SnailgunHelloWorld
import snailgun.utils.SnailgunEcho
import snailgun.utils.SnailgunArgEcho

import java.io.PrintStream
import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.{ExecutionException, TimeUnit}
import java.nio.charset.StandardCharsets
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

import monix.eval.Task
import monix.execution.Scheduler

import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

import com.martiansoftware.nailgun.NGListeningAddress
import com.martiansoftware.nailgun.NGConstants
import com.martiansoftware.nailgun.Alias
import com.martiansoftware.nailgun.{
  SnailgunThreadLocalInputStream,
  NGServer,
  ThreadLocalPrintStream
}

class SnailgunBaseSuite extends BaseSuite {
  protected final val TestPort = 8313
  private final val nailgunPool = Scheduler.computation(parallelism = 2)

  def startServer[T](streams: Streams, logger: Logger)(
      op: Client => T
  ): Task[T] = {
    /*
     * This code tricks nailgun into thinking it has already set up the streams
     * and wrapped them with their own thread local-based wrappers. We do this
     * to avoid Nailgun running `System.setIn`, `System.setOut` and
     * `System.setErr` which would affect all tests run in the suite and
     * effectively hide input/outputs.
     */
    val currentIn = System.in
    val currentOut = System.out
    val currentErr = System.err

    // Some dummy streams that we use initially
    val serverIn = new PipedInputStream()
    val clientOut = new PipedOutputStream(serverIn)
    val clientIn = new PipedInputStream()
    val serverOut = new PrintStream(new PipedOutputStream(clientIn))
    val serverErr = new PrintStream(new ByteArrayOutputStream())

    val localIn = new SnailgunThreadLocalInputStream(serverIn)
    val localOut = new ThreadLocalPrintStream(serverOut)
    val localErr = new ThreadLocalPrintStream(serverOut)

    localIn.init(serverIn)
    localOut.init(serverOut)
    localErr.init(serverErr)

    System.in.synchronized {
      System.setIn(localIn)
      System.setOut(localOut)
      System.setErr(localErr)
    }

    val addr = InetAddress.getLoopbackAddress
    val serverIsStarted = scala.concurrent.Promise[Unit]()
    val serverIsFinished = scala.concurrent.Promise[Unit]()
    val serverLogic = Task {
      try {
        val server =
          prepareTestServer(localIn, localOut, localErr, addr, TestPort, logger)
        serverIsStarted.success(())
        server.run()
        serverIsFinished.success(())
      } catch {
        case NonFatal(t) =>
          currentErr.println("Error when starting server")
          t.printStackTrace(currentErr)
          serverIsStarted.failure(t)
          serverIsFinished.failure(t)
      } finally {
        serverOut.flush()
        serverErr.flush()
      }
    }

    val client = new TcpClient(addr, TestPort)
    def clientCancel(t: Option[Throwable]) = Task {
      serverOut.flush()
      serverErr.flush()

      t.foreach(t => logger.trace(t))

      logger.debug("Exiting server...")
      val code = client.run(
        "exit",
        new Array(0),
        Defaults.cwd,
        Defaults.env,
        streams,
        logger,
        new AtomicBoolean(false),
        true
      )

      // Exit on Windows can sometimes return non-successful code even if exit succeeded
      if (isWindows) {
        if (code != 0) {
          logger.debug(s"The status code for exit in Windows was ${code}.")
        }
      } else {
        assert(code == 0)
      }

      System.in.synchronized {
        System.setIn(currentIn)
        System.setOut(currentOut)
        System.setErr(currentErr)
      }

    }

    val runClientLogic = Task(op(client))
      .doOnFinish(clientCancel(_))
      .doOnCancel(clientCancel(None))

    val startTrigger = Task.fromFuture(serverIsStarted.future)
    val endTrigger = Task.fromFuture(serverIsFinished.future)
    val runClient = {
      for {
        _ <- startTrigger
        value <- runClientLogic
        _ <- endTrigger
      } yield value
    }

    Task
      .parZip2(serverLogic, runClient)
      .map(t => t._2)
      .timeout(FiniteDuration(25, TimeUnit.SECONDS))
  }

  def prepareTestServer(
      in: InputStream,
      out: PrintStream,
      err: PrintStream,
      addr: InetAddress,
      port: Int,
      logger: Logger
  ): NGServer = {
    val javaLogger = new Slf4jAdapter(logger)
    val address = new NGListeningAddress(addr, port)
    val poolSize = NGServer.DEFAULT_SESSIONPOOLSIZE
    val heartbeatMs = NGConstants.HEARTBEAT_TIMEOUT_MILLIS.toInt
    val server =
      new NGServer(address, poolSize, heartbeatMs, in, out, err, javaLogger)
    val aliases = server.getAliasManager
    aliases.addAlias(
      new Alias(
        "heartbeat",
        "Run `Heartbeat` naigun server example.",
        classOf[SnailgunHeartbeat]
      )
    )
    aliases.addAlias(
      new Alias(
        "echo",
        "Run `Echo` naigun server example.",
        classOf[SnailgunEcho]
      )
    )
    aliases.addAlias(
      new Alias(
        "hello-world",
        "Run `HelloWorld` naigun server example.",
        classOf[SnailgunHelloWorld]
      )
    )
    aliases.addAlias(
      new Alias(
        "exit",
        "Run `exit` on the nail main defined in this class.",
        classOf[ExitNail]
      )
    )
    server
  }

  /**
   * Starts a Nailgun server, creates a snailgun client and executes operations with that client.
   * The server is killed when the client exits.
   *
   * @param streams
   *   The user-defined streams.
   * @param log
   *   The logger instance for the test run.
   * @param op
   *   A function that will receive the instantiated Client.
   * @return
   *   The result of executing `op` on the client.
   */
  def withRunningServer[T](
      streams: Streams,
      logger: Logger
  )(op: Client => T): T = {
    val f = startServer(streams, logger)(op).runToFuture(nailgunPool)
    try Await.result(f, FiniteDuration(5, TimeUnit.SECONDS))
    catch {
      case e: ExecutionException => throw e.getCause()
      case t: Throwable => throw t
    } finally f.cancel()
  }

  case class TestInputs(
      streams: Streams,
      logger: RecordingLogger,
      stop: AtomicBoolean,
      private val client: Client,
      private val out: ByteArrayOutputStream
  ) {
    def run(cmd: String, args: Array[String]): Int =
      client.run(cmd, args, Defaults.cwd, Defaults.env, streams, logger, stop, true)

    lazy val output: String = {
      new String(out.toByteArray(), StandardCharsets.UTF_8)
    }
  }

  val oldErr = System.err
  def testSnailgun(
      testName: String,
      in: InputStream = System.in
  )(op: TestInputs => Unit): Unit = {
    val logger = new RecordingLogger()
    val stop = new AtomicBoolean(false)
    test(testName) {
      try {
        val out = new ByteArrayOutputStream()
        val streams = Streams(in, out, out)
        withRunningServer(streams, logger) { client =>
          op(TestInputs(streams, logger, stop, client, out))
        }
      } catch {
        case t: TimeoutException =>
          logger.dump(oldErr);
          stop.set(true)
          throw t
      }
    }
  }
}
