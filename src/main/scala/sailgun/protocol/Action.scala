package sailgun.protocol

import java.io.OutputStream

sealed trait Action
object Action {
  final case object SendStdin extends Action
  final case class Exit(code: Int) extends Action
  final case class ExitForcefully(error: Throwable) extends Action
  final case class Print(bytes: Array[Byte], out: OutputStream) extends Action {
    override def toString: String = s"Print(${bytes.toString})"
  }
}
