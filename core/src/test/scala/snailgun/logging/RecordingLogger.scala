package snailgun.logging

import java.io.PrintStream
import java.util.concurrent.ConcurrentLinkedQueue

import scala.collection.JavaConverters.asScalaIteratorConverter

class RecordingLogger(
    debug: Boolean = false,
    debugOut: Option[PrintStream] = None
) extends Logger {
  override val isVerbose: Boolean = true
  override val name: String = "SnailgunRecordingLogger"
  private[this] val messages = new ConcurrentLinkedQueue[(String, String)]

  def clear(): Unit = messages.clear()
  def debugs: List[String] = getMessagesAt(Some("debug"))
  def infos: List[String] = getMessagesAt(Some("info"))
  def warnings: List[String] = getMessagesAt(Some("warn"))
  def errors: List[String] = getMessagesAt(Some("error"))

  def getMessagesAt(level: Option[String]): List[String] =
    getMessages(level).map(_._2)

  def getMessages(): List[(String, String)] = getMessages(None)
  private def getMessages(level: Option[String]): List[(String, String)] = {
    val initialMsgs = messages.iterator.asScala
    val msgs = level match {
      case Some(level) => initialMsgs.filter(_._1 == level)
      case None => initialMsgs
    }

    msgs.map {
      // Remove trailing '\r' so that we don't have to special case for Windows
      case (category, msg) => (category, msg.stripSuffix("\r"))
    }.toList
  }

  def add(key: String, value: String): Unit = {
    if (debug) {
      debugOut match {
        case Some(o) => o.println(s"[$key] $value")
        case None => println(s"[$key] $value")
      }
    }

    messages.add((key, value))
    ()
  }

  override def debug(msg: String): Unit = add("debug", msg)
  override def info(msg: String): Unit = add("info", msg)
  override def error(msg: String): Unit = add("error", msg)
  override def warn(msg: String): Unit = add("warn", msg)
  private def trace(msg: String): Unit = add("trace", msg)
  override def trace(ex: Throwable): Unit = {
    ex.getStackTrace.foreach(ste => trace(ste.toString))
    Option(ex.getCause).foreach { cause =>
      trace("Caused by:")
      trace(cause)
    }
  }

  def dump(out: PrintStream = System.out): Unit = {
    out.println {
      s"""Logger contains the following messages:
         |${getMessages
          .map(s => s"[${s._1}] ${s._2}")
          .mkString("\n  ", "\n  ", "\n")}
     """.stripMargin
    }
  }

  def render: String = {
    getMessages()
      .map { case (level, msg) => s"[${level}] ${msg}" }
      .mkString(System.lineSeparator())
  }
}
