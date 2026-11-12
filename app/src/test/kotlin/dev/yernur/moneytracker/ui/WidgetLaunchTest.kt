package dev.yernur.moneytracker.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.AppContainer
import dev.yernur.moneytracker.LocalAppContainer
import dev.yernur.moneytracker.data.db.AppDatabase
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetLaunchTest {
    @get:Rule val compose = createComposeRule()
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()

    @After
    fun tearDown() = db.close()

    @Test
    fun aQuickAddWidgetTap_opensTheFullAddScreenOnItsKind() {
        var launch by mutableStateOf<AppLaunch?>(null)
        compose.setContent {
            CompositionLocalProvider(LocalAppContainer provides AppContainer(db)) { AppTheme { AppRoot(launch = launch) } }
        }
        compose.runOnIdle { launch = AppLaunch("transfer", null) } // the tap arrives once the app is up (MainActivity.onNewIntent)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("save").fetchSemanticsNodes().isNotEmpty() } // the regular add screen, with its pinned Add button
        compose.onNodeWithTag("kind-Transfer").assertIsSelected()
    }
}
