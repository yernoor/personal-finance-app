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
    fun navbarAndHomeLayout_defaultToTheStandardShape_andPersist() {
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
    fun iconStyles_haveSeparateChoicesForGlassAndNot_andNeverMixThem() {
        prefs.edit().clear().commit()
        val settings = AppSettings(prefs)
        assertEquals(IconStyle.Springboard, settings.iconStyle.value)
        assertEquals(IconStyle.LiquidTile, settings.glassIconStyle.value) // the liquid tile is the glass default

        settings.setIconStyle(IconStyle.SoftFill)
        settings.setGlassIconStyle(IconStyle.ClearGlass)
        val again = AppSettings(prefs)
        assertEquals(IconStyle.SoftFill, again.iconStyle.value)
        assertEquals(IconStyle.ClearGlass, again.glassIconStyle.value)

        settings.setIconStyle(IconStyle.LiquidTile) // a glass look is not allowed as the everyday choice, and vice versa
        settings.setGlassIconStyle(IconStyle.SoftFill)
        assertEquals(IconStyle.SoftFill, settings.iconStyle.value)
        assertEquals(IconStyle.ClearGlass, settings.glassIconStyle.value)

        prefs.edit().putString("iconStyle", "ClearGlass").putString("glassIconStyle", "Springboard").commit()
        val repaired = AppSettings(prefs)
        assertEquals(IconStyle.Springboard, repaired.iconStyle.value)
        assertEquals(IconStyle.LiquidTile, repaired.glassIconStyle.value)

        prefs.edit().putString("iconStyle", "Vivid").putString("glassIconStyle", "GlassTile").commit() // removed looks fall back to the defaults
        val migrated = AppSettings(prefs)
        assertEquals(IconStyle.Springboard, migrated.iconStyle.value)
        assertEquals(IconStyle.LiquidTile, migrated.glassIconStyle.value)
    }

    @Test
    fun liquidGlassIsOffByDefault() {
        prefs.edit().clear().commit()
        assertEquals(false, AppSettings(prefs).glass.value)
    }
}
