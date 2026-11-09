package dev.yernur.moneytracker.data.feedback

import java.io.File
import java.time.Instant
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashLogTest {
    private fun log() = CrashLog(File(createTempDirectory().toFile(), "crash/last.txt"))
    private val boom = IllegalStateException("secret 4500.00 KZT Kaspi").apply {
        stackTrace = arrayOf(StackTraceElement("dev.yernur.moneytracker.ui.HomeScreen", "draw", "HomeScreen.kt", 88))
    }

    @Test fun `nothing is pending until something is recorded, and clear removes it`() {
        val log = log()
        assertNull(log.pending())
        log.record(boom, "main", Instant.parse("2026-11-10T09:00:00Z"))
        assertNotNull(log.pending())
        log.clear()
        assertNull(log.pending())
    }

    @Test fun `the report has class, frames and cause chain but never the message`() {
        val wrapped = RuntimeException("outer detail 123", boom)
        val text = describe(wrapped)
        assertTrue(text.startsWith("java.lang.RuntimeException\n"))
        assertTrue(text.contains("Caused by: java.lang.IllegalStateException"))
        assertTrue(text.contains("at dev.yernur.moneytracker.ui.HomeScreen.draw(HomeScreen.kt:88)"))
        assertFalse(text.contains("secret") || text.contains("4500") || text.contains("outer detail"))
    }

    @Test fun `the title names the exception and the first app frame`() {
        val log = log()
        log.record(boom, "main", Instant.parse("2026-11-10T09:00:00Z"))
        assertEquals("Crash: IllegalStateException in HomeScreen.kt:88", crashTitle(log.pending()!!))
    }

    @Test fun `the installed handler records the crash and still calls the previous handler`() {
        val before = Thread.getDefaultUncaughtExceptionHandler()
        var forwarded: Throwable? = null
        Thread.setDefaultUncaughtExceptionHandler { _, e -> forwarded = e }
        try {
            val log = log()
            log.install()
            Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), boom)
            assertTrue(log.pending()!!.contains("IllegalStateException"))
            assertEquals(boom, forwarded)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(before)
        }
    }
}
