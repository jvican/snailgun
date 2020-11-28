package snailgun.protocol

import java.io.InputStream
import java.io.OutputStream

/**
 * An instance of user-defined streams where the protocol will forward any
 * stdout, stdin or stderr coming from the client.
 *
 * Note that this is decoupled from the logger API, which is mostly used for
 * tracing the protocol behaviour and reporting errors. The logger can be
 * backed by some of these user-defined streams but it isn't a requirement.
 */
case class Streams(in: InputStream, out: OutputStream, err: OutputStream)
