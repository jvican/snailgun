package sailgun

import sailgun.utils.{Diff, DiffAssertions}

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.language.experimental.macros
import scala.collection.JavaConverters._
import scala.reflect.ClassTag

import utest.TestSuite
import utest.Tests
import utest.asserts.Asserts
import utest.framework.Formatter
import utest.framework.TestCallTree
import utest.framework.Tree
import utest.ufansi.Attrs
import utest.ufansi.Str
import utest.ufansi.Color

import monix.eval.Task
import java.{util => ju}

class BaseSuite extends TestSuite {
  val pprint = _root_.pprint.PPrinter.BlackWhite
  val OS = System.getProperty("os.name").toLowerCase(ju.Locale.ENGLISH)
  val isWindows: Boolean = OS.contains("windows")

  def isAppveyor: Boolean = "True" == System.getenv("APPVEYOR")
  def beforeAll(): Unit = ()
  def afterAll(): Unit = ()
  def intercept[T: ClassTag](exprs: Unit): T = macro Asserts.interceptProxy[T]

  def assertNotEmpty(string: String): Unit = {
    if (string.isEmpty) {
      fail(
        s"expected non-empty string, obtained empty string."
      )
    }
  }

  def assertEmpty(string: String): Unit = {
    if (!string.isEmpty) {
      fail(
        s"expected empty string, obtained: $string"
      )
    }
  }

  def assertContains(string: String, substring: String): Unit = {
    assert(string.contains(substring))
  }

  def assertNotContains(string: String, substring: String): Unit = {
    assert(!string.contains(substring))
  }

  def assert(exprs: Boolean*): Unit = macro Asserts.assertProxy

  def assertNotEquals[T](obtained: T, expected: T, hint: String = ""): Unit = {
    if (obtained == expected) {
      val hintMsg = if (hint.isEmpty) "" else s" (hint: $hint)"
      assertNoDiff(obtained.toString, expected.toString, hint)
      fail(s"obtained=<$obtained> == expected=<$expected>$hintMsg")
    }
  }

  def assertEquals[T](obtained: T, expected: T, hint: String = ""): Unit = {
    if (obtained != expected) {
      val hintMsg = if (hint.isEmpty) "" else s" (hint: $hint)"
      assertNoDiff(obtained.toString, expected.toString, hint)
      fail(s"obtained=<$obtained> != expected=<$expected>$hintMsg")
    }
  }

  def assertNoDiff(
      obtained: String,
      expected: String,
      obtainedTitle: String,
      expectedTitle: String
  )(implicit filename: sourcecode.File, line: sourcecode.Line): Unit = {
    colored {
      DiffAssertions.assertNoDiffOrPrintExpected(
        obtained,
        expected,
        obtainedTitle,
        expectedTitle,
        true
      )
      ()
    }
  }

  def assertNoDiff(
      obtained: String,
      expected: String,
      title: String = "",
      print: Boolean = true
  )(implicit filename: sourcecode.File, line: sourcecode.Line): Unit = {
    colored {
      DiffAssertions.assertNoDiffOrPrintExpected(obtained, expected, title, title, print)
      ()
    }
  }

  def colored[T](
      thunk: => T
  )(implicit filename: sourcecode.File, line: sourcecode.Line): T = {
    try {
      thunk
    } catch {
      case scala.util.control.NonFatal(e) =>
        val message = e.getMessage.linesIterator
          .map { line =>
            if (line.startsWith("+")) Color.Green(line)
            else if (line.startsWith("-")) Color.LightRed(line)
            else Color.Reset(line)
          }
          .mkString("\n")
        val location = s"failed assertion at ${filename.value}:${line.value}\n"
        throw new DiffAssertions.TestFailedException(location + message)
    }
  }

  import monix.execution.CancelableFuture
  import java.util.concurrent.TimeUnit
  import scala.concurrent.duration.FiniteDuration
  def waitForDuration[T](future: CancelableFuture[T], duration: FiniteDuration)(
      ifError: => Unit
  ): T = {
    import java.util.concurrent.TimeoutException
    try Await.result(future, duration)
    catch {
      case t: TimeoutException => ifError; throw t
    }
  }

  def waitInSeconds[T](future: CancelableFuture[T], seconds: Int)(ifError: => Unit): T = {
    waitForDuration(future, FiniteDuration(seconds.toLong, TimeUnit.SECONDS))(ifError)
  }

  def waitInMillis[T](future: CancelableFuture[T], ms: Int)(ifError: => Unit): T = {
    waitForDuration(future, FiniteDuration(ms.toLong, TimeUnit.MILLISECONDS))(ifError)
  }

  override def utestAfterAll(): Unit = afterAll()
  override def utestFormatter: Formatter = new Formatter {
    override def exceptionMsgColor: Attrs = Attrs.Empty
    override def exceptionStackFrameHighlighter(
        s: StackTraceElement
    ): Boolean = {
      s.getClassName.startsWith("bloop.") &&
      !(s.getClassName.startsWith("bloop.util") ||
        s.getClassName.startsWith("bloop.testing"))
    }

    override def formatWrapWidth: Int = 3000
    override def formatException(x: Throwable, leftIndent: String): Str =
      super.formatException(x, "")
  }

  case class FlatTest(name: String, thunk: () => Unit)
  private val myTests = IndexedSeq.newBuilder[FlatTest]

  def ignore(name: String, label: String = "IGNORED")(fun: => Any): Unit = {
    myTests += FlatTest(
      utest.ufansi.Color.LightRed(s"$label - $name").toString(),
      () => ()
    )
  }

  def test(name: String)(fun: => Any): Unit = {
    myTests += FlatTest(name, () => { fun; () })
  }

  implicit lazy val testScheduler = {
    monix.execution.Scheduler.Implicits.global
  }

  def testAsync(name: String, maxDuration: Duration = Duration("20s"))(
      run: => Unit
  ): Unit = {
    test(name) {
      Await.result(Task { run }.runAsync(testScheduler), maxDuration)
    }
  }

  def fail(msg: String, stackBump: Int = 0): Nothing = {
    val ex = new DiffAssertions.TestFailedException(msg)
    ex.setStackTrace(ex.getStackTrace.slice(1 + stackBump, 5 + stackBump))
    throw ex
  }

  override def tests: Tests = {
    val ts = myTests.result()
    val names = Tree("", ts.map(x => Tree(x.name)): _*)
    val thunks = new TestCallTree({
      this.beforeAll()
      Right(ts.map(x => new TestCallTree(Left(x.thunk()))))
    })
    Tests(names, thunks)
  }
}
