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

    @Test
    fun navbarAndHomeLayout_defaultToTheOriginalShape_andPersist() {
        prefs.edit().clear().commit()
        val fresh = AppSettings(prefs)
        assertEquals(listOf("home", "history", "charts", "accounts"), fresh.navTabs.value)
        assertEquals(listOf("warnings", "balance", "totals"), fresh.homeCards.value)
        assertEquals(PeriodUnit.Month, fresh.homeTotalsPeriod.value) // the monthly totals the owner likes

        fresh.setNavTabs(listOf("history", "categories", "home"))
        fresh.setHomeCards(listOf("recent", "balance"))
        fresh.setHomeTotalsPeriod(PeriodUnit.Week)
        val again = AppSettings(prefs)
        assertEquals(listOf("history", "categories", "home"), again.navTabs.value)
        assertEquals(listOf("recent", "balance"), again.homeCards.value)
        assertEquals(PeriodUnit.Week, again.homeTotalsPeriod.value)
    }

    @Test
    fun anInvalidNavbarIsIgnored() {
        prefs.edit().clear().commit()
        val settings = AppSettings(prefs)
        settings.setNavTabs(listOf("home")) // one tab is not a navigation bar
        assertEquals(AppSettings.NAV_DEFAULT, settings.navTabs.value)
    }

    @Test
    fun iconStyle_defaultsToVivid_persists_andNeverStoresGlass() {
        prefs.edit().clear().commit()
        val settings = AppSettings(prefs)
        assertEquals(IconStyle.Vivid, settings.iconStyle.value)

        settings.setIconStyle(IconStyle.Duotone)
        assertEquals(IconStyle.Duotone, AppSettings(prefs).iconStyle.value)

        settings.setIconStyle(IconStyle.Glass) // glass icons are automatic with liquid glass, never a stored choice
        assertEquals(IconStyle.Duotone, settings.iconStyle.value)
        prefs.edit().putString("iconStyle", "Glass").commit()
        assertEquals(IconStyle.Vivid, AppSettings(prefs).iconStyle.value)
    }

    @Test
    fun liquidGlassIsOffByDefault() {
        prefs.edit().clear().commit()
        assertEquals(false, AppSettings(prefs).glass.value)
    }
}
