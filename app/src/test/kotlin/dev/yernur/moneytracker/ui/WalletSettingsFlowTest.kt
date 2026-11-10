package dev.yernur.moneytracker.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.AppContainer
import dev.yernur.moneytracker.LocalAppContainer
import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.db.AccountEntity
import dev.yernur.moneytracker.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** More -> Automation: nothing is read until the owner consents, and the default account is remembered. */
@RunWith(RobolectricTestRunner::class)
class WalletSettingsFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: AppDatabase
    private val settings = AppSettings()

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        db.accountDao().insertAll(listOf(account("main", "Main"), account("dep", "Deposit")))
        compose.setContent {
            CompositionLocalProvider(LocalAppContainer provides AppContainer(db, settings)) { AppTheme { AppRoot() } }
        }
    }

    @After
    fun tearDown() = db.close()

    private fun account(uid: String, title: String) = AccountEntity(uid, title, "KZT", "cash", null, 0, null, false, true, false, false, "", "")

    private fun eventually(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition()) {
            check(System.nanoTime() < deadline) { "condition not met within 5 s" }
            compose.waitForIdle()
            Thread.sleep(20)
        }
    }

    private fun openMoreTag(tag: String) {
        compose.onNodeWithTag("more").performClick()
        compose.onNodeWithTag(tag).performScrollTo().performClick()
    }

    @Test
    fun consentIsAskedFirst_cancelLeavesCaptureOff_allowTurnsItOn() {
        assertFalse(settings.walletCapture.value)
        openMoreTag("wallet-capture")
        compose.onNodeWithText("Read Google Wallet payments?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertFalse(settings.walletCapture.value)

        compose.onNodeWithTag("wallet-capture").performClick()
        compose.onNodeWithTag("wallet-allow").performClick()
        assertTrue(settings.walletCapture.value)

        compose.onNodeWithTag("wallet-capture").performClick() // tapping the row again switches it off, no dialog
        assertFalse(settings.walletCapture.value)
    }

    @Test
    fun defaultAccountCanBePickedAndIsRemembered() {
        assertNull(settings.walletAccountUid.value)
        openMoreTag("wallet-account")
        eventually { compose.onAllNodesWithText("Main").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Main").performClick()
        assertEquals("main", settings.walletAccountUid.value)
        eventually { compose.onAllNodesWithText("Main").fetchSemanticsNodes().isNotEmpty() } // shown under the row now
    }
}
