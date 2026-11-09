package dev.yernur.moneytracker.data.feedback

import java.io.File
import java.time.Instant

/**
 * Keeps the last uncaught exception in a file so the next launch can offer to report it. Exception *messages* are left out on
 * purpose: they can quote typed text, SQL values or file paths, and the report ends up on a public GitHub page. Class names and
 * stack frames are enough to find a bug.
 */
class CrashLog(private val file: File) {
    /** The saved report, or null when the last run did not crash. */
    fun pending(): String? = file.takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

    fun clear() {
        file.delete()
    }

    fun record(error: Throwable, thread: String, now: Instant = Instant.now()) {
        file.parentFile?.mkdirs()
        file.writeText("When: $now\nThread: $thread\n\n${describe(error)}")
    }

    /** Chains in front of the previous default handler, so the system still shows its own crash dialog and kills the process. */
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(error, thread.name) } // a failing write must never hide the original crash
            previous?.uncaughtException(thread, error)
        }
    }
}

private const val MAX_FRAMES = 30
private const val MAX_CAUSES = 4

/** Exception class and `at` lines for the error and its causes, no messages. */
fun describe(error: Throwable): String = generateSequence(error) { it.cause }.take(MAX_CAUSES).mapIndexed { i, e ->
    val head = (if (i == 0) "" else "Caused by: ") + e.javaClass.name
    (listOf(head) + e.stackTrace.take(MAX_FRAMES).map { "    at $it" }).joinToString("\n")
}.joinToString("\n")

/** A one-line issue title: the exception class and where it was thrown, e.g. "Crash: IllegalStateException in HomeScreen.kt:88". */
fun crashTitle(report: String): String {
    val lines = report.lines()
    val cls = lines.firstOrNull { it.isNotBlank() && !it.startsWith("When:") && !it.startsWith("Thread:") }?.substringAfterLast('.') ?: "unknown"
    val frame = lines.firstOrNull { it.startsWith("    at dev.yernur") }?.substringAfterLast('(')?.removeSuffix(")")
    return "Crash: $cls" + if (frame != null) " in $frame" else ""
}
