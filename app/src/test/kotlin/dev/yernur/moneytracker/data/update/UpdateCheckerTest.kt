package dev.yernur.moneytracker.data.update

import dev.yernur.moneytracker.data.sheets.FakeTransport
import dev.yernur.moneytracker.data.sheets.HttpResponse
import dev.yernur.moneytracker.data.sheets.ok
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdateCheckerTest {
    /** The real answer GitHub gave for this repo's v0.2.0 release (public data). */
    private val realRelease = javaClass.getResource("/github_release_v0.2.0.json")!!.readText()

    private fun checker(installed: String, response: HttpResponse) = UpdateChecker(FakeTransport { response }, installed)

    @Test
    fun versionsAreComparedNumberByNumber() {
        assertEquals(listOf(0, 2, 1), parseVersion("v0.2.1"))
        assertEquals(listOf(0, 2, 1), parseVersion("0.2.1"))
        assertNull(parseVersion("nightly"))
        assertNull(parseVersion("v1.x"))
        assertTrue(isNewer(parseVersion("0.10.0")!!, parseVersion("0.9.0")!!)) // as strings "0.10.0" would sort before "0.9.0"
        assertTrue(isNewer(listOf(0, 2, 1), listOf(0, 2)))
        assertFalse(isNewer(listOf(1), listOf(1, 0, 0)))
        assertFalse(isNewer(listOf(0, 2, 0), listOf(0, 2, 1)))
    }

    @Test
    fun theRealRelease_isNewerThanAnOlderInstall_withItsApkAndChecksum() {
        val release = checker("0.1.0", ok(realRelease)).check()!!
        assertEquals("v0.2.0", release.tag)
        assertEquals("https://github.com/yernoor/personal-finance-app/releases/tag/v0.2.0", release.pageUrl)
        val apk = release.apk!!
        assertEquals("money-tracker-v0.2.0.apk", apk.name)
        assertEquals("https://github.com/yernoor/personal-finance-app/releases/download/v0.2.0/money-tracker-v0.2.0.apk", apk.url)
        assertEquals(49_058_287L, apk.size)
        assertEquals("7122501b210b0657c9dfdfac40fa1dc0f67559153869dd95101b43de7d8e38aa", apk.sha256)
    }

    @Test
    fun theSameOrANewerInstall_hasNothingToUpdate() {
        assertNull(checker("0.2.0", ok(realRelease)).check())
        assertNull(checker("0.3.0", ok(realRelease)).check())
        assertNull(checker("local-build", ok(realRelease)).check()) // an odd version name is never nagged
    }

    @Test
    fun noReleaseYet_isNotAnError() {
        assertNull(checker("0.1.0", HttpResponse(404, """{"message":"Not Found"}""")).check())
    }

    @Test
    fun aReleaseWithoutAnApk_stillOffersThePage() {
        val body = """{"tag_name":"v0.3.0","html_url":"https://example/r","assets":[{"name":"notes.txt","browser_download_url":"https://example/n"}]}"""
        val release = checker("0.2.0", ok(body)).check()!!
        assertNull(release.apk)
        assertEquals("https://example/r", release.pageUrl)
    }

    @Test
    fun anApkWithoutADigest_hasNoChecksumToCompare() {
        val body = """{"tag_name":"v0.3.0","html_url":"u","assets":[{"name":"a.apk","browser_download_url":"d","size":5,"digest":null}]}"""
        assertNull(checker("0.2.0", ok(body)).check()!!.apk!!.sha256)
    }

    @Test
    fun problemsAreExplainedInPlainWords() {
        val limited = assertThrows(UpdateException::class.java) { checker("0.1.0", HttpResponse(403, "{}")).check() }
        assertTrue(limited.message!!.contains("limiting"))
        assertTrue(assertThrows(UpdateException::class.java) { checker("0.1.0", HttpResponse(500, "")).check() }.message!!.contains("HTTP 500"))
        val oddTag = """{"tag_name":"nightly","html_url":"u","assets":[]}"""
        assertTrue(assertThrows(UpdateException::class.java) { checker("0.1.0", ok(oddTag)).check() }.message!!.contains("nightly"))
    }

    @Test
    fun itAsksTheRightAddress() {
        val transport = FakeTransport { ok(realRelease) }
        UpdateChecker(transport, "0.1.0").check()
        assertEquals("https://api.github.com/repos/yernoor/personal-finance-app/releases/latest", transport.requests.single().url)
    }
}
