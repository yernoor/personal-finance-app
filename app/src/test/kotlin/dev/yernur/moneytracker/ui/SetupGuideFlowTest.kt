package dev.yernur.moneytracker.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.AppContainer
import dev.yernur.moneytracker.LocalAppContainer
import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.db.AccountEntity
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.CategoryEntity
import dev.yernur.moneytracker.data.db.TransactionEntity
import dev.yernur.moneytracker.data.db.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The setup guide opens by itself only on a fresh install, can be stepped through and closed, and reopens from More. */
@RunWith(RobolectricTestRunner::class)
class SetupGuideFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: AppDatabase
    private val settings = AppSettings().apply { setSetupGuideSeen(false) }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun launch() = compose.setContent {
        CompositionLocalProvider(LocalAppContainer provides AppContainer(db, settings)) { AppTheme { AppRoot() } }
    }

    private fun eventually(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition()) {
            check(System.nanoTime() < deadline) { "condition not met within 5 s" }
            compose.waitForIdle()
            Thread.sleep(20)
        }
    }

    private fun tapPages(x: Float) = compose.onNodeWithTag("guide-pages").performTouchInput { click(Offset(width * x, height / 2f)) }

    private fun shown(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun hasText(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun freshInstall_opensGuide_nextThroughToStart() {
        settings.setReduceMotion(true) // nothing advances by itself, so the buttons decide the page
        launch()
        eventually { shown("setup-guide") }
        assertTrue(settings.setupGuideSeen.value)
        assertTrue(hasText("1 of 5"))
        repeat(4) { tapPages(0.8f) } // right side: forward
        eventually { hasText("5 of 5") && shown("guide-start") }
        tapPages(0.1f) // left third: back
        eventually { hasText("4 of 5") && !shown("guide-start") }
        tapPages(0.8f)
        eventually { shown("guide-start") }
        compose.onNodeWithTag("guide-start").performClick()
        eventually { !shown("setup-guide") && shown("home-balance") }
        eventually { shown("home-setup") && hasText("0 of 5") } // the checklist comes on with the guide
    }

    @Test
    fun checklist_ticksFromData_andHides() {
        settings.setReduceMotion(true)
        launch()
        eventually { shown("setup-guide") }
        compose.onNodeWithTag("guide-skip").performClick()
        eventually { shown("home-setup") && hasText("0 of 5") }
        runBlocking { db.accountDao().insertAll(listOf(AccountEntity("main", "Main", "KZT", "cash", null, 0, null, false, true, false, false, "", ""))) }
        eventually { hasText("1 of 5") }
        settings.setWalletCapture(true)
        eventually { hasText("2 of 5") }
        compose.onNodeWithTag("setup-hide").performSemanticsAction(SemanticsActions.OnClick) // under the floating bar on the tiny test screen
        eventually { !settings.setupCard.value }
        eventually { !shown("home-setup") }
    }

    @Test
    fun checklist_disappearsWhenEveryStepIsDone() {
        settings.setSetupGuideSeen(true)
        settings.setSetupCard(true)
        settings.setWalletCapture(true)
        settings.markAnalysisSeen()
        runBlocking { db.accountDao().insertAll(listOf(AccountEntity("main", "Main", "KZT", "cash", null, 0, null, false, true, false, false, "", ""))) }
        launch()
        eventually { hasText("3 of 5") }
        runBlocking {
            db.categoryDao().insertAll(listOf(CategoryEntity("cafe", "Cafe", TransactionType.Expense, "cafe", null, 0, null, null, 0, false, "", "")))
            db.transactionDao().insertAll(listOf(TransactionEntity("t", TransactionType.Expense, "main", "cafe", 100, null, null, 100, null, "2026-11-24", null, "", "")))
        }
        eventually { !shown("home-setup") }
    }

    @Test
    fun withMotion_pagesMoveOnByThemselves() {
        launch()
        eventually { shown("setup-guide") }
        eventually { hasText("5 of 5") } // the test clock runs each page's bar to the end
    }

    @Test
    fun existingData_marksSeen_withoutOpeningGuide() {
        runBlocking { db.accountDao().insertAll(listOf(AccountEntity("main", "Main", "KZT", "cash", null, 0, null, false, true, false, false, "", ""))) }
        launch()
        eventually { settings.setupGuideSeen.value }
        compose.waitForIdle()
        assertTrue(!shown("setup-guide"))
        assertTrue(!settings.setupCard.value && !shown("home-setup")) // the owner, upgrading, never gets the checklist either
    }

    @Test
    fun reopensFromMore_andSkipCloses() {
        settings.setSetupGuideSeen(true)
        settings.setReduceMotion(true)
        launch()
        compose.onNodeWithTag("more").performClick()
        compose.onNodeWithTag("section-about").performScrollTo().performClick()
        compose.onNodeWithTag("open-guide").performScrollTo().performClick()
        eventually { shown("setup-guide") }
        compose.onNodeWithTag("guide-skip").performClick()
        eventually { !shown("setup-guide") }
    }
}
