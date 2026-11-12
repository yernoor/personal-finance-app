package dev.yernur.moneytracker.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.AppContainer
import dev.yernur.moneytracker.LocalAppContainer
import dev.yernur.moneytracker.data.db.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EntrySheetTest {
    @get:Rule val compose = createComposeRule()
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()

    @After
    fun tearDown() = db.close()

    @Test
    fun sheetMode_collapseLeaves_andExpandHandsOverTheKind() {
        var left = 0
        var expanded: EntryKind? = null
        compose.setContent {
            CompositionLocalProvider(LocalAppContainer provides AppContainer(db)) {
                AppTheme { EntryScreen(null, EntryKind.Income, onLeave = { left++ }, sheet = true, onExpand = { expanded = it }) }
            }
        }
        compose.onNodeWithTag("expand").performClick()
        assertEquals(EntryKind.Income, expanded) // the sheet opens on the kind the widget asked for
        compose.onNodeWithTag("collapse").performClick()
        assertEquals(1, left)
    }
}
