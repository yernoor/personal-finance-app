package dev.yernur.moneytracker.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.AppContainer
import dev.yernur.moneytracker.LocalAppContainer
import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.db.AppDatabase
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeTotalsOpenHistoryTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        compose.setContent { CompositionLocalProvider(LocalAppContainer provides AppContainer(db, AppSettings())) { AppTheme { AppRoot() } } }
    }

    @After
    fun tearDown() = db.close()

    private fun waitForTag(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun tappingIncome_opensHistoryOnThisMonthsIncome() {
        waitForTag("home-income")
        compose.onNodeWithTag("home-income").performClick()
        waitForTag("chart-income")
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("chart-income").assertIsSelected(); true }.getOrDefault(false) }
        compose.onNodeWithTag("period-Month").assertIsSelected()
    }

    @Test
    fun tappingExpenses_opensHistoryOnThisMonthsExpenses() {
        waitForTag("home-expenses")
        compose.onNodeWithTag("home-expenses").performClick()
        waitForTag("chart-expenses")
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("chart-expenses").assertIsSelected(); true }.getOrDefault(false) }
        compose.onNodeWithTag("period-Month").assertIsSelected()
    }
}
