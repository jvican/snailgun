package snailgun

import snailgun.logging.Logger
import snailgun.logging.SnailgunLogger
import snailgun.protocol.Defaults
import snailgun.protocol.Protocol
import snailgun.protocol.Streams

import java.net.Socket
import java.nio.file.Paths
import java.nio.file.Path
import java.io.PrintStream
import java.io.InputStream
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.net.SocketException

class TcpClient(addr: InetAddress, port: Int) extends Client {
  def run(
      cmd: String,
      args: Array[String],
      cwd: Path,
      env: Map[String, String],
      streams: Streams,
      logger: Logger,
      stop: AtomicBoolean
  ): Int = {
    val socket = new Socket(addr, port)
    try {
      val in = socket.getInputStream()
      val out = socket.getOutputStream()
      val protocol = new Protocol(streams, cwd, env, logger, stop)
      protocol.sendCommand(cmd, args, out, in)
    } finally {
      try {
        if (socket.isClosed()) ()
        else {
          try socket.shutdownInput()
          finally {
            try socket.shutdownOutput()
            finally socket.close()
          }
        }
      } catch {
        case t: SocketException =>
          logger.debug("Tracing an ignored socket exception...")
          logger.trace(t)
          ()
      }
    }
  }
}

object TcpClient {
  def apply(host: String, port: Int): TcpClient = {
    new TcpClient(InetAddress.getByName(host), port)
  }
}
