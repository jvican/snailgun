package com.martiansoftware.nailgun

import java.io.InputStream

final class SailgunThreadLocalInputStream(stream: InputStream)
    extends ThreadLocalInputStream(stream) {
  override def init(streamForCurrentThread: InputStream): Unit =
    super.init(streamForCurrentThread)
}
