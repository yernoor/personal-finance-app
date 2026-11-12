package dev.yernur.moneytracker.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.AppContainer
import dev.yernur.moneytracker.LocalAppContainer
import dev.yernur.moneytracker.data.db.AppDatabase
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DateSelectorTest {
    @get:Rule val compose = createComposeRule()
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()

    @After
    fun tearDown() = db.close()

    private fun count(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    @Test
    fun quickSegmentsSetTheDate_andTheCalendarSegmentOpensTheMonthInPlace() {
        compose.setContent { CompositionLocalProvider(LocalAppContainer provides AppContainer(db)) { AppTheme { EntryScreen(null, EntryKind.Expense, onLeave = {}) } } }
        compose.waitUntil(10_000) { count("date-0") > 0 }
        compose.onNodeWithTag("date-0").assertIsSelected()
        compose.onNodeWithTag("date-1").performScrollTo().performClick()
        val yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy"))
        compose.onNodeWithTag("date-text").assertTextContains(yesterday)
        compose.onNodeWithTag("date-1").assertIsSelected()
        assertEquals(0, count("date-calendar"))
        compose.onNodeWithTag("date-3").performScrollTo().performClick()
        compose.waitUntil(10_000) { count("date-calendar") > 0 }
        compose.onNodeWithTag("date-3").assertIsSelected() // the thumb moves to the calendar while it is open
    }
}
