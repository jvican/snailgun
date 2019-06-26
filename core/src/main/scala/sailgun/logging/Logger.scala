package sailgun.logging

abstract class Logger {
  val name: String
  val isVerbose: Boolean

  def debug(msg: String): Unit
  def error(msg: String): Unit
  def warn(msg: String): Unit
  def info(msg: String): Unit
  def trace(exception: Throwable): Unit
}
