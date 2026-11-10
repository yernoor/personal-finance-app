package dev.yernur.moneytracker.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.security.AppLock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LockScreenTest {
    @get:Rule val compose = createComposeRule()

    /** The PIN check runs on a background thread and resumes on the main looper, which Robolectric only runs when told to. */
    private fun awaitBackground(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            compose.waitForIdle()
            Thread.sleep(20)
        }
    }

    private fun type(digits: String) = digits.forEach { compose.onNodeWithTag("pin-$it").performClick() }

    @Test
    fun wrongPinShowsAMessage_rightPinUnlocks() {
        val settings = AppSettings()
        AppLock(settings).setPin("1234")
        val lock = AppLock(settings)
        compose.setContent { AppTheme { LockScreen(lock, 4) } }

        type("1111")
        awaitBackground { compose.onAllNodesWithText("Wrong PIN").fetchSemanticsNodes().isNotEmpty() } // the check runs off the main thread
        assertTrue(lock.locked.value)

        type("1234")
        awaitBackground { !lock.locked.value }
        assertFalse(lock.locked.value)
    }

    @Test
    fun settingAPin_needsTheSamePinTwice() {
        var chosen: String? = null
        compose.setContent { AppTheme { PinSetupDialog({}) { chosen = it } } }

        type("2468"); compose.onNodeWithTag("pin-confirm").performClick()
        type("2469"); compose.onNodeWithTag("pin-confirm").performClick() // differs: starts over
        assertTrue(chosen == null)
        type("2468"); compose.onNodeWithTag("pin-confirm").performClick()
        type("2468"); compose.onNodeWithTag("pin-confirm").performClick()
        assertTrue(chosen == "2468")
    }
}
