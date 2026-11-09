package dev.yernur.moneytracker.ui

import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.AppContainer
import dev.yernur.moneytracker.LocalAppContainer
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.feedback.CrashLog
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class FeedbackScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `send needs a title and opens a prefilled issue of the repo`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        compose.setContent { CompositionLocalProvider(LocalAppContainer provides AppContainer(db)) { AppTheme { FeedbackScreen {} } } }

        compose.onNodeWithTag("send-feedback").assertIsNotEnabled()
        compose.onNodeWithTag("kind-Idea").performClick()
        compose.onNodeWithTag("feedback-title").performTextInput("Dark chart")
        compose.onNodeWithTag("send-feedback").performScrollTo().assertIsEnabled().performClick()

        val intent = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        val url = intent.data.toString()
        assertTrue(url, url.startsWith("https://github.com/yernoor/personal-finance-app/issues/new?labels=enhancement&title=Dark%20chart"))
        db.close()
    }
}

@RunWith(RobolectricTestRunner::class)
class CrashPromptTest {
    @get:Rule val compose = createComposeRule()

    private fun show(log: CrashLog, onReport: () -> Unit = {}) {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        compose.setContent { CompositionLocalProvider(LocalAppContainer provides AppContainer(db, crashes = log)) { AppTheme { CrashPrompt(onReport) } } }
    }

    private fun log(record: Boolean) = CrashLog(File(createTempDirectory().toFile(), "c.txt")).also {
        if (record) it.record(IllegalStateException(), "main")
    }

    @Test fun `no prompt without a saved crash`() {
        show(log(record = false))
        compose.onAllNodesWithTag("crash-prompt").assertCountEquals(0)
    }

    @Test fun `not now discards the trace`() {
        val log = log(record = true)
        show(log)
        compose.onNodeWithTag("crash-dismiss").performClick()
        assertNull(log.pending())
        compose.onAllNodesWithTag("crash-prompt").assertCountEquals(0)
    }

    @Test fun `review keeps the trace for the report screen`() {
        val log = log(record = true)
        var opened = false
        show(log) { opened = true }
        compose.onNodeWithTag("crash-report").performClick()
        assertTrue(opened)
        assertNotNull(log.pending())
    }
}
