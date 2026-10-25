package dev.yernur.moneytracker.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppSettingsTest {
    private val prefs = ApplicationProvider.getApplicationContext<Application>().getSharedPreferences("settings-test", 0)

    @Test
    fun historyOpensOnTheDayByDefault_andRemembersTheChoice() {
        prefs.edit().clear().commit()
        assertEquals(PeriodUnit.Day, AppSettings(prefs).historyPeriod.value)

        AppSettings(prefs).setHistoryPeriod(PeriodUnit.Month)
        assertEquals(PeriodUnit.Month, AppSettings(prefs).historyPeriod.value) // a new instance reads it back
    }

    @Test
    fun anUnknownStoredPeriodFallsBackToTheDay() {
        prefs.edit().putString("historyPeriod", "Fortnight").commit()
        assertEquals(PeriodUnit.Day, AppSettings(prefs).historyPeriod.value)
    }
}
