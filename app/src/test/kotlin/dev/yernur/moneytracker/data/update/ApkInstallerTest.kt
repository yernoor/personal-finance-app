package dev.yernur.moneytracker.data.update

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ApkInstallerTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val bytes = ByteArray(200_000) { (it % 251).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun installer(content: ByteArray = bytes) = ApkInstaller(context, open = { _, _ -> Opened(ByteArrayInputStream(content), false) })
    private fun apk(size: Long = bytes.size.toLong(), sha256: String? = sha) = ApkAsset("update.apk", "https://example/update.apk", size, sha256)

    @Test
    fun aGoodDownload_isWrittenToTheCache_andReportsProgressUpToOne() {
        val progress = mutableListOf<Float>()
        val file = installer().download(apk()) { progress += it }
        assertTrue(file.path.startsWith(context.cacheDir.path))
        assertEquals(bytes.size.toLong(), file.length())
        assertEquals(1f, progress.last(), 0f)
        assertTrue(progress.zipWithNext().all { (a, b) -> a <= b })
    }

    @Test
    fun aFileThatDoesNotMatchTheChecksum_isRejectedAndDeleted() {
        val tampered = bytes.copyOf().also { it[10] = 99 }
        val error = assertThrows(UpdateException::class.java) { installer(tampered).download(apk()) {} }
        assertTrue(error.message!!.contains("checksum"))
        assertFalse(java.io.File(context.cacheDir, "updates/update.apk").exists())
    }

    @Test
    fun aShortDownload_isRejected() {
        val error = assertThrows(java.io.IOException::class.java) { installer(bytes.copyOf(1_000)).download(apk(sha256 = null)) {} }
        assertTrue(error.message!!.contains("incomplete"))
        assertEquals(1_000L, java.io.File(context.cacheDir, "updates/update.apk.part").length()) // kept, so the next try resumes
    }

    @Test
    fun aCutOffDownload_resumesFromThePartialFile() {
        val offsets = mutableListOf<Long>()
        val cutOff = object : java.io.InputStream() {
            var sent = 0
            override fun read(): Int = throw java.io.IOException("reset")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (sent >= 70_000) throw java.io.IOException("reset")
                val n = minOf(len, 70_000 - sent)
                System.arraycopy(bytes, sent, b, off, n)
                sent += n
                return n
            }
        }
        val first = ApkInstaller(context, open = { _, offset -> offsets += offset; Opened(cutOff, false) })
        assertThrows(java.io.IOException::class.java) { first.download(apk()) {} }
        assertTrue(java.io.File(context.cacheDir, "updates/update.apk.part").length() == 70_000L)

        val second = ApkInstaller(context, open = { _, offset -> offsets += offset; Opened(ByteArrayInputStream(bytes, offset.toInt(), bytes.size), true) })
        val file = second.download(apk()) {}
        assertEquals(listOf(0L, 70_000L), offsets)
        assertEquals(bytes.size.toLong(), file.length())
        assertTrue(file.readBytes().contentEquals(bytes))
    }

    @Test
    fun aServerThatIgnoresRange_restartsFromZero() {
        installer().download(apk()) {}.delete()
        java.io.File(context.cacheDir, "updates/update.apk.part").writeBytes(bytes.copyOf(5_000))
        val file = installer().download(apk()) {}
        assertTrue(file.readBytes().contentEquals(bytes))
    }

    @Test
    fun withoutAChecksum_theSizeStillHasToMatch_andAnOlderDownloadIsDropped() {
        installer().download(apk()) {}
        val second = ApkAsset("second.apk", "https://example/second.apk", bytes.size.toLong(), null)
        installer().download(second) {}
        assertFalse(java.io.File(context.cacheDir, "updates/update.apk").exists())
        assertTrue(java.io.File(context.cacheDir, "updates/second.apk").exists())
    }

    @Test
    fun aSignatureMismatch_isExplainedInPlainWords_otherFailuresKeepAndroidsText() {
        val android = "INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package dev.yernur.moneytracker signatures do not match newer version; ignoring!"
        assertTrue(describeInstallFailure(5, android).contains("different key"))
        assertEquals("INSTALL_FAILED_INSUFFICIENT_STORAGE", describeInstallFailure(6, "INSTALL_FAILED_INSUFFICIENT_STORAGE"))
        assertEquals("The install failed (code 1).", describeInstallFailure(1, null))
    }
}
