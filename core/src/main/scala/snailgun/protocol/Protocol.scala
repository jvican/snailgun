package snailgun.protocol

import snailgun.Terminal
import snailgun.logging.Logger

import java.net.Socket
import java.io.OutputStream
import java.io.PrintStream
import java.io.InputStream
import java.io.DataOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStreamReader
import java.io.BufferedReader

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files
import java.nio.ByteBuffer

import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

import scala.util.Try
import scala.util.Failure
import scala.util.Success
import scala.util.control.NonFatal

/**
 * An implementation of the nailgun protocol in Scala.
 *
 * It follows http://www.martiansoftware.com/nailgun/protocol.html and has been slightly inspired in
 * the C and Python clients. The implementation has been simplified more than these two and
 * optimized for readability.
 *
 * The protocol is designed to be used by different instances of [[snailgun.Client]] implementing
 * different communication mechanisms (e.g. TCP / Unix Domain sockets / Windows Named Pipes).
 */
class Protocol(
    streams: Streams,
    cwd: Path,
    environment: Map[String, String],
    logger: Logger,
    stopFurtherProcessing: AtomicBoolean,
    interactiveSession: Boolean
) {
  private val absoluteCwd = cwd.toAbsolutePath().toString
  private val exitCode: AtomicInteger = new AtomicInteger(-1)
  private val isRunning: AtomicBoolean = new AtomicBoolean(false)
  private val anyThreadFailed: AtomicBoolean = new AtomicBoolean(false)
  private val sendStdinSemaphore: Semaphore = new Semaphore(0)
  private val waitTermination: Semaphore = new Semaphore(0)

  val NailgunFileSeparator = java.io.File.separator
  val NailgunPathSeparator = java.io.File.pathSeparator
  def allEnvironment: Map[String, String] = {
    def interactive(fd: Int): String =
      Integer.toString(Terminal.hasTerminalAttached(fd))
    def skipIfNative(f: => String) =
      if (!interactiveSession || System.getProperty("java.vm.name") == "Substrate VM") "0" else f
    environment ++ Map(
      "NAILGUN_FILESEPARATOR" -> NailgunFileSeparator,
      "NAILGUN_PATHSEPARATOR" -> NailgunPathSeparator,
      "NAILGUN_TTY_0" -> skipIfNative(interactive(0)),
      "NAILGUN_TTY_1" -> skipIfNative(interactive(1)),
      "NAILGUN_TTY_2" -> skipIfNative(interactive(2))
    )
  }

  def sendCommand(
      cmd: String,
      cmdArgs: Array[String],
      out0: OutputStream,
      in0: InputStream
  ): Int = {
    isRunning.set(true)
    val in = new DataInputStream(in0)
    val out = new DataOutputStream(out0)

    val sendStdin = createStdinThread(out)
    val scheduleHeartbeat = createHeartbeatAndShutdownThread(in, out)
    // Start heartbeat thread before sending command as python and C clients do
    scheduleHeartbeat.start()

    try {
      // Send client command's environment to Nailgun server
      logger.debug(s"Sending arguments '${cmdArgs.mkString(" ")}' to Nailgun server")
      cmdArgs.foreach(sendChunk(ChunkTypes.Argument, _, out))
      logger.debug("Sending environment variables to Nailgun server")
      allEnvironment.foreach(kv => sendChunk(ChunkTypes.Environment, s"${kv._1}=${kv._2}", out))
      logger.debug(s"Sending working directory $absoluteCwd to Nailgun server")
      sendChunk(ChunkTypes.Directory, absoluteCwd, out)
      logger.debug(s"Sending command to $cmd Nailgun server")
      sendChunk(ChunkTypes.Command, cmd, out)
      logger.debug("Finished sending command information to Nailgun server")

      // Start thread sending stdin right after sending command
      logger.debug("Starting thread to read stdin...")
      sendStdin.start()

      while (exitCode.get() == -1) {
        val action = processChunkFromServer(in)
        logger.debug(s"Received action $action from Nailgun server")
        action match {
          case Action.Exit(code) =>
            exitCode.compareAndSet(-1, code)
          case Action.ExitForcefully(error) =>
            if (cmd == "ng-stop") {
              // In previous versions to 1.0.0, ng-stop can throw EOFException
              exitCode.compareAndSet(-1, 0)
            } else {
              exitCode.compareAndSet(-1, 1)
              printException(error)
            }
          case Action.Print(bytes, out) => out.write(bytes)
          case Action.SendStdin => sendStdinSemaphore.release()
        }
      }
    } catch {
      case NonFatal(exception) =>
        exitCode.compareAndSet(-1, 1)
        if (!stopFurtherProcessing.get()) {
          printException(exception)
        }
    } finally {
      // Always disable `isRunning` when client finishes the command execution
      isRunning.compareAndSet(true, false)
      // Release with max to guarantee all `acquire` return
      waitTermination.release(Int.MaxValue)
      // Release stdin semaphore if `acquire` was done by `sendStdin` thread
      sendStdinSemaphore.release(Int.MaxValue)
    }

    if (stopFurtherProcessing.get()) {
      sendStdin.interrupt()
    }

    logger.debug("Waiting for stdin thread to finish...")
    sendStdin.join()
    logger.debug("Waiting for heartbeat thread to finish...")
    scheduleHeartbeat.join()
    logger.debug("Returning exit code...")
    exitCode.get()
  }

  def sendChunk(
      tpe: ChunkTypes.ChunkType,
      msg: String,
      out: DataOutputStream
  ): Unit = {
    val payload = msg.getBytes(StandardCharsets.UTF_8)
    out.writeInt(payload.length)
    out.writeByte(tpe.toByteRepr.toInt)
    out.write(payload)
    out.flush()
  }

  def processChunkFromServer(in: DataInputStream): Action = {
    def readPayload(length: Int, in: DataInputStream): Array[Byte] = {
      var total: Int = 0
      val bytes = new Array[Byte](length)
      while (total < length) {
        val read = in.read(bytes, total, length - total)
        if (read < 0) {
          // Error before reaching goal of read bytes
          throw new EOFException("Couldn't read bytes from server")
        } else {
          total += read
        }
      }
      bytes
    }

    val readAction = Try {
      val bytesToRead = in.readInt()
      val chunkType = in.readByte()
      chunkType match {
        case ChunkTypes.SendInput.toByteRepr =>
          Action.SendStdin
        case ChunkTypes.Stdout.toByteRepr =>
          Action.Print(readPayload(bytesToRead, in), streams.out)
        case ChunkTypes.Stderr.toByteRepr =>
          Action.Print(readPayload(bytesToRead, in), streams.err)
        case ChunkTypes.Exit.toByteRepr =>
          val bytes = readPayload(bytesToRead, in)
          val code =
            Integer.parseInt(new String(bytes, StandardCharsets.US_ASCII).trim)
          Action.Exit(code)
        case _ =>
          val error = new RuntimeException(s"Unexpected chunk type: $chunkType")
          Action.ExitForcefully(error)
      }
    }

    readAction match {
      case Success(action) => action
      case Failure(exception) => Action.ExitForcefully(exception)
    }
  }

  def createHeartbeatAndShutdownThread(
      in: DataInputStream,
      out: DataOutputStream
  ): Thread = {
    daemonThread { () =>
      var continue: Boolean = true
      while (continue) {
        val acquired = waitTermination.tryAcquire(
          Defaults.Time.DefaultHeartbeatIntervalMillis,
          TimeUnit.MILLISECONDS
        )
        if (acquired) {
          continue = false
        } else {
          swallowExceptionsIfServerFinished {
            if (stopFurtherProcessing.get()) {
              out.synchronized {
                out.flush()
                try in.close()
                finally out.close()
              }
            }
            out.synchronized {
              sendChunk(ChunkTypes.Heartbeat, "", out)
            }
          }
        }
      }
    }
  }

  def createStdinThread(out: DataOutputStream): Thread = {
    daemonThread { () =>
      val reader = new BufferedReader(new InputStreamReader(streams.in))
      def shouldStop = !isRunning.get() || stopFurtherProcessing.get()
      try {
        var continue: Boolean = true
        while (continue) {
          if (shouldStop) {
            continue = false
          } else {
            // Don't start sending input until SendStdin action is received from server
            sendStdinSemaphore.acquire()
            if (shouldStop) {
              continue = false
            } else {
              val line = reader.readLine()
              if (shouldStop) {
                continue = false
              } else if (line.length() == 0) {
                () // Ignore if read line is empty
              } else {
                swallowExceptionsIfServerFinished {
                  out.synchronized {
                    if (line == null) sendChunk(ChunkTypes.StdinEOF, "", out)
                    else sendChunk(ChunkTypes.Stdin, line, out)
                  }
                }
              }
            }
          }
        }
      } finally reader.close()
    }
  }

  /**
   * Swallows any exception thrown by the closure [[f]] if client exits before the timeout of
   * [[Protocol.Time.SendThreadWaitTerminationMillis]].
   *
   * Ignoring exceptions in this scenario makes sense (exception could have been caught by server
   * finishing connection with client concurrently).
   */
  private def swallowExceptionsIfServerFinished(f: => Unit): Unit = {
    try f
    catch {
      case NonFatal(exception) =>
        // Should always be false while client waits for exit code from server
        val acquired = waitTermination.tryAcquire(
          Defaults.Time.SendThreadWaitTerminationMillis,
          TimeUnit.MILLISECONDS
        )

        // Ignore exception if in less than the wait the client exited
        if (acquired) ()
        else throw exception
    }
  }

  private def printException(exception: Throwable): Unit = {
    logger.error("Unexpected error forces client exit!")
    logger.trace(exception)
  }

  private def daemonThread(run0: () => Unit): Thread = {
    val t = new Thread {
      override def run(): Unit = {
        try run0()
        catch {
          case NonFatal(exception) =>
            if (anyThreadFailed.compareAndSet(false, true)) {
              printException(exception)
            }
        }
      }
    }
    t.setDaemon(true)
    t
  }
}
