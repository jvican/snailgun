package snailgun.logging

import java.io.PrintStream

class SnailgunLogger(
    override val name: String,
    out: PrintStream,
    override val isVerbose: Boolean
) extends Logger {
  def debug(msg: String): Unit = if (isVerbose) out.println(s"debug: $msg") else ()
  def error(msg: String): Unit = out.println(s"error: $msg")
  def warn(msg: String): Unit = out.println(s"warn: $msg")
  def info(msg: String): Unit = out.println(s"$msg")
  def trace(exception: Throwable): Unit = exception.printStackTrace(out)
}
