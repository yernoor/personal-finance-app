package dev.yernur.moneytracker.walletlogger

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Placeholder values only (no real merchants or amounts). */
@RunWith(RobolectricTestRunner::class)
class RecorderTest {
    private lateinit var store: LogStore
    private val recorder get() = Recorder(store, OWN)

    @Before
    fun setUp() {
        store = LogStore(ApplicationProvider.getApplicationContext())
        store.clear()
        store.setAllowedPackages(setOf(LogStore.WALLET_PACKAGE))
    }

    @After
    fun tearDown() = store.clear()

    private fun sample(pkg: String, text: String? = "KZT1,234.00 with Test Card ••0000") =
        LoggedNotification(pkg, 1_700_000_000_000, 7, false, "SOME SHOP", text, null)

    @Test
    fun walletNotificationIsStoredAndReadsBack() {
        recorder.onPosted(LogStore.WALLET_PACKAGE) { sample(LogStore.WALLET_PACKAGE) }
        assertEquals(listOf(sample(LogStore.WALLET_PACKAGE)), store.entries())
    }

    @Test
    fun otherPackageContentIsNeverReadOrStored() {
        var read = false
        recorder.onPosted("com.example.chat") { read = true; sample("com.example.chat", "secret message") }
        assertFalse("content of a non-listed app must not be read", read)
        assertTrue(store.entries().isEmpty())
        assertFalse(store.exportText().contains("secret"))
    }

    @Test
    fun otherPackageIsCountedByNameOnly() {
        repeat(3) { recorder.onPosted("com.example.bank") { error("must not be read") } }
        assertEquals(mapOf("com.example.bank" to 3), store.seenPackages())
    }

    @Test
    fun ownNotificationsAreIgnored() {
        recorder.onPosted(OWN) { error("must not be read") }
        assertTrue(store.seenPackages().isEmpty())
    }

    @Test
    fun addingAPackageToTheAllowListStartsCapturingIt() {
        store.setAllowedPackages(setOf(LogStore.WALLET_PACKAGE, "com.example.bank"))
        recorder.onPosted("com.example.bank") { sample("com.example.bank") }
        assertEquals(1, store.entries().size)
        assertTrue(store.seenPackages().isEmpty())
    }

    @Test
    fun nullFieldsAndTheSummaryFlagSurviveTheRoundTrip() {
        val summary = LoggedNotification(LogStore.WALLET_PACKAGE, 5, 1, true, null, null, null)
        recorder.onPosted(LogStore.WALLET_PACKAGE) { summary }
        assertEquals(summary, store.entries().single())
        assertNull(store.entries().single().title)
    }

    @Test
    fun logIsCappedAtTheNewestEntries() {
        val small = LogStore(ApplicationProvider.getApplicationContext(), maxEntries = 3)
        (1..5).forEach { small.append(sample(LogStore.WALLET_PACKAGE, "n$it")) }
        assertEquals(listOf("n3", "n4", "n5"), small.entries().map { it.text })
    }

    @Test
    fun clearRemovesEntriesAndSeenPackages() {
        recorder.onPosted(LogStore.WALLET_PACKAGE) { sample(LogStore.WALLET_PACKAGE) }
        recorder.onPosted("com.example.bank") { error("must not be read") }
        store.clear()
        assertTrue(store.entries().isEmpty() && store.seenPackages().isEmpty())
    }

    private companion object {
        const val OWN = "dev.yernur.moneytracker.walletlogger"
    }
}
