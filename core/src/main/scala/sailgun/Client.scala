package sailgun

import sailgun.logging.Logger
import sailgun.protocol.Streams
import sailgun.protocol.Defaults

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

abstract class Client {
  def run(
      cmd: String,
      args: Array[String],
      cwd: Path,
      env: Map[String, String],
      streams: Streams,
      logger: Logger,
      stop: AtomicBoolean
  ): Int

  def run(
      cmd: String,
      args: Array[String],
      streams: Streams,
      logger: Logger,
      stop: AtomicBoolean
  ): Int = run(cmd, args, Defaults.cwd, Defaults.env, streams, logger, stop)
}
