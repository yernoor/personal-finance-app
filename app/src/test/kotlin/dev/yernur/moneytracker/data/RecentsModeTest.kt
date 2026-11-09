package dev.yernur.moneytracker.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.data.security.RecentsMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecentsModeTest {
    private fun modeStored(name: String?): RecentsMode {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("recents-test", Context.MODE_PRIVATE)
        prefs.edit().clear().apply { if (name != null) putString("recentsMode", name) }.commit()
        return AppSettings(prefs).recentsMode.value
    }

    @Test fun missingMeansOff() = assertEquals(RecentsMode.Off, modeStored(null))
    @Test fun hideIsKept() = assertEquals(RecentsMode.Hide, modeStored("Hide"))
    // Blur was removed; a phone that had it chosen must stay private.
    @Test fun oldBlurBecomesHide() = assertEquals(RecentsMode.Hide, modeStored("Blur"))
}
