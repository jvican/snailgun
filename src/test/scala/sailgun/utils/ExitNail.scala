package sailgun.utils

import com.martiansoftware.nailgun.NGContext

class ExitNail
object ExitNail {
  def nailMain(ngContext: NGContext): Unit = {
    val server = ngContext.getNGServer
    import java.util.concurrent.ForkJoinPool

    ForkJoinPool
      .commonPool()
      .submit(new Runnable {
        override def run(): Unit = {
          server.shutdown(false)
        }
      })

    ()
  }

}
