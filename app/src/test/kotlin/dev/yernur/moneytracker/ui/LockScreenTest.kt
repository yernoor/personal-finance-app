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

    private fun type(digits: String) = digits.forEach { compose.onNodeWithTag("pin-$it").performClick() }

    @Test
    fun wrongPinShowsAMessage_rightPinUnlocks() {
        val settings = AppSettings()
        AppLock(settings).setPin("1234")
        val lock = AppLock(settings)
        compose.setContent { AppTheme { LockScreen(lock, 4) } }

        type("1111")
        compose.waitForIdle()
        assertTrue(lock.locked.value)
        assertTrue(compose.onAllNodesWithText("Wrong PIN").fetchSemanticsNodes().isNotEmpty())

        type("1234")
        compose.waitForIdle()
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
