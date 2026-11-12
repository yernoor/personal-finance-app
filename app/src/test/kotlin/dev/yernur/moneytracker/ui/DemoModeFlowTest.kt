package dev.yernur.moneytracker.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.AppContainer
import dev.yernur.moneytracker.LocalAppContainer
import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.db.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** More -> About: demo mode goes on after a confirmation, and back to the real data only with the PIN when one is set. */
@RunWith(RobolectricTestRunner::class)
class DemoModeFlowTest {
    @get:Rule val compose = createComposeRule()
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
    private val switches = mutableListOf<Boolean>()

    @After
    fun tearDown() = db.close()

    private fun start(demo: Boolean, pin: String? = null) {
        val container = AppContainer(db, AppSettings(), demoMode = demo, switchDemoMode = { switches += it })
        if (pin != null) container.lock.setPin(pin) // after the container: the app is not locked at start
        compose.setContent { CompositionLocalProvider(LocalAppContainer provides container) { AppTheme { AppRoot() } } }
    }

    /** The PIN check runs off the main thread (as in LockScreenTest). */
    private fun awaitBackground(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            compose.waitForIdle()
            Thread.sleep(20)
        }
    }

    private fun openAbout(tag: String) {
        compose.onNodeWithTag("more").performClick()
        compose.onNodeWithTag("section-about").performScrollTo().performClick()
        compose.onNodeWithTag(tag).performScrollTo().performClick()
    }

    @Test
    fun turningDemoOn_asksFirst() {
        start(demo = false)
        compose.onNodeWithTag("demo-badge").assertDoesNotExist()
        openAbout("demo-on")
        assertEquals(emptyList<Boolean>(), switches)
        compose.onNodeWithTag("confirm-demo").performClick()
        assertEquals(listOf(true), switches)
    }

    @Test
    fun leavingTheDemo_withoutAPin_isOneTap() {
        start(demo = true)
        compose.onNodeWithTag("demo-badge").assertExists()
        openAbout("demo-off")
        assertEquals(listOf(false), switches)
    }

    @Test
    fun leavingTheDemo_withAPin_needsTheRightPin() {
        start(demo = true, pin = "2468") // no 0: that key sits on the bottom row, below the dialog on the small test screen
        openAbout("demo-off")
        "1111".forEach { compose.onNodeWithTag("pin-$it").performClick() }
        compose.waitForIdle()
        assertEquals(emptyList<Boolean>(), switches)
        awaitBackground { compose.onAllNodesWithText("Wrong PIN").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Enter your current PIN").assertExists()
        compose.onNodeWithText("Wrong PIN").assertExists()
        "2468".forEach { compose.onNodeWithTag("pin-$it").performClick() }
        awaitBackground { switches.isNotEmpty() }
        assertEquals(listOf(false), switches)
    }

    @Test
    fun theSheetsScreen_onlyHasStubs() {
        start(demo = true)
        compose.onNodeWithTag("more").performClick()
        compose.onNodeWithTag("section-data").performScrollTo().performClick()
        compose.onNodeWithTag("open-sync").performScrollTo().performClick()
        compose.onNodeWithTag("sync-connect").assertDoesNotExist()
        compose.onNodeWithTag("sync-demo-now").performClick()
        compose.onNodeWithTag("sync-demo-note").assertExists()
    }
}
