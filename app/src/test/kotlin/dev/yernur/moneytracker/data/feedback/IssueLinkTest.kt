package dev.yernur.moneytracker.data.feedback

import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueLinkTest {
    private val info = deviceInfo("0.4.0", "16", 36, "Pixel 8")

    private fun param(url: String, name: String) =
        URLDecoder.decode(url.substringAfter("?").split("&").first { it.startsWith("$name=") }.substringAfter("="), "UTF-8")

    @Test fun `points at the new-issue page of the repo with the label`() {
        val url = issueUrl("owner/repo", FeedbackKind.Idea, "t", "d", info)
        assertTrue(url.startsWith("https://github.com/owner/repo/issues/new?"))
        assertEquals("enhancement", param(url, "labels"))
    }

    @Test fun `title and body survive encoding, spaces and unicode included`() {
        val url = issueUrl("o/r", FeedbackKind.Bug, "Кафе & чай = 100%", "line 1\nline 2", info)
        assertFalse(url.contains('+'))
        assertEquals("Кафе & чай = 100%", param(url, "title"))
        assertEquals("line 1\nline 2\n\n---\nApp 0.4.0, Android 16 (API 36), Pixel 8", param(url, "body"))
    }

    @Test fun `a long body is cut so the link stays loadable`() {
        val url = issueUrl("o/r", FeedbackKind.Crash, "t", "x".repeat(20_000), info)
        assertEquals(5000, param(url, "body").length)
    }
}
