package dev.yernur.moneytracker.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** User preferences. [prefs] null keeps everything in memory (tests). */
class AppSettings(private val prefs: SharedPreferences? = null) {
    private val _warnThreshold = MutableStateFlow(prefs?.getInt(WARN_THRESHOLD, DEFAULT_THRESHOLD) ?: DEFAULT_THRESHOLD)
    private val _notifications = MutableStateFlow(prefs?.getBoolean(NOTIFICATIONS, true) ?: true)
    private val _historyPeriod = MutableStateFlow(
        prefs?.getString(HISTORY_PERIOD, null)?.let { name -> PeriodUnit.entries.firstOrNull { it.name == name } } ?: PeriodUnit.Day,
    )
    private val _navTabs = MutableStateFlow(
        OrderedChoice.sanitize(prefs?.getString(NAV_TABS, null)?.split(","), NAV_POOL, NAV_DEFAULT, NAV_MIN, NAV_MAX),
    )
    private val _homeCards = MutableStateFlow(
        OrderedChoice.sanitize(prefs?.getString(HOME_CARDS, null)?.split(","), HOME_POOL, HOME_DEFAULT, HOME_MIN, HOME_POOL.size),
    )
    private val _homeTotalsPeriod = MutableStateFlow(
        prefs?.getString(HOME_PERIOD, null)?.let { name -> PeriodUnit.entries.firstOrNull { it.name == name } } ?: PeriodUnit.Month,
    )
    private val _iconStyle = MutableStateFlow(storedIconStyle(prefs, ICON_STYLE, glass = false, IconStyle.Vivid))
    private val _glassIconStyle = MutableStateFlow(storedIconStyle(prefs, GLASS_ICON_STYLE, glass = true, IconStyle.GlassTile))
    private val _glass = MutableStateFlow(prefs?.getBoolean(GLASS, false) ?: false)
    private val warned = HashSet<String>(prefs?.getStringSet(WARNED, emptySet()).orEmpty())

    /** Warn when a projection passes this percent of a category limit (categories can override). */
    val warnThreshold: StateFlow<Int> = _warnThreshold
    val notifications: StateFlow<Boolean> = _notifications

    /** What the History tab opens on. The original app defaults to the day (`defaultPeriod = day` in its backup). */
    val historyPeriod: StateFlow<PeriodUnit> = _historyPeriod

    /** The icon look while liquid glass is off. Defaults to vivid discs. */
    val iconStyle: StateFlow<IconStyle> = _iconStyle

    fun setIconStyle(style: IconStyle) {
        if (style.forGlass) return
        _iconStyle.value = style
        prefs?.edit()?.putString(ICON_STYLE, style.name)?.apply()
    }

    /** The icon look while liquid glass is on (only glass looks are allowed there). Defaults to the frosted tile. */
    val glassIconStyle: StateFlow<IconStyle> = _glassIconStyle

    fun setGlassIconStyle(style: IconStyle) {
        if (!style.forGlass) return
        _glassIconStyle.value = style
        prefs?.edit()?.putString(GLASS_ICON_STYLE, style.name)?.apply()
    }

    /** Experimental liquid glass look (floating glass bar, add button, sheets, dialogs). Off by default. */
    val glass: StateFlow<Boolean> = _glass

    fun setGlass(enabled: Boolean) {
        _glass.value = enabled
        prefs?.edit()?.putBoolean(GLASS, enabled)?.apply()
    }

    /** Ids of the bottom-bar tabs, in order (2 to 5 of [NAV_POOL]). */
    val navTabs: StateFlow<List<String>> = _navTabs

    fun setNavTabs(tabs: List<String>) {
        _navTabs.value = OrderedChoice.sanitize(tabs, NAV_POOL, NAV_DEFAULT, NAV_MIN, NAV_MAX)
        prefs?.edit()?.putString(NAV_TABS, _navTabs.value.joinToString(","))?.apply()
    }

    /** Ids of the cards on Home, in order. The default keeps the original's monthly totals. */
    val homeCards: StateFlow<List<String>> = _homeCards

    fun setHomeCards(cards: List<String>) {
        _homeCards.value = OrderedChoice.sanitize(cards, HOME_POOL, HOME_DEFAULT, HOME_MIN, HOME_POOL.size)
        prefs?.edit()?.putString(HOME_CARDS, _homeCards.value.joinToString(","))?.apply()
    }

    /** The period the Home totals and top-categories cards cover. */
    val homeTotalsPeriod: StateFlow<PeriodUnit> = _homeTotalsPeriod

    fun setHomeTotalsPeriod(unit: PeriodUnit) {
        _homeTotalsPeriod.value = unit
        prefs?.edit()?.putString(HOME_PERIOD, unit.name)?.apply()
    }

    fun setHistoryPeriod(unit: PeriodUnit) {
        _historyPeriod.value = unit
        prefs?.edit()?.putString(HISTORY_PERIOD, unit.name)?.apply()
    }

    fun setWarnThreshold(percent: Int) {
        _warnThreshold.value = percent.coerceIn(1, 1000)
        prefs?.edit()?.putInt(WARN_THRESHOLD, _warnThreshold.value)?.apply()
    }

    fun setNotifications(enabled: Boolean) {
        _notifications.value = enabled
        prefs?.edit()?.putBoolean(NOTIFICATIONS, enabled)?.apply()
    }

    /** Each (month, category, warning kind) notifies once. */
    @Synchronized fun isWarned(key: String) = key in warned

    @Synchronized fun markWarned(key: String) {
        warned.add(key)
        prefs?.edit()?.putStringSet(WARNED, HashSet(warned))?.apply()
    }

    companion object {
        const val DEFAULT_THRESHOLD = 100
        val NAV_POOL = listOf("home", "history", "charts", "accounts", "categories", "tags")
        val NAV_DEFAULT = NAV_POOL.take(4)
        const val NAV_MIN = 2
        const val NAV_MAX = 5
        val HOME_POOL = listOf("warnings", "balance", "totals", "top-categories", "recent")
        val HOME_DEFAULT = listOf("warnings", "balance", "totals")
        const val HOME_MIN = 1
        private const val WARN_THRESHOLD = "warnThreshold"
        private const val NOTIFICATIONS = "notifications"
        private const val WARNED = "warned"
        private const val HISTORY_PERIOD = "historyPeriod"
        private const val NAV_TABS = "navTabs"
        private const val GLASS = "glass"
        private const val ICON_STYLE = "iconStyle"
        private const val GLASS_ICON_STYLE = "glassIconStyle"
        private const val HOME_CARDS = "homeCards"
        private const val HOME_PERIOD = "homeTotalsPeriod"
    }
}

/** A stored style, accepted only if it belongs to the right set (glass looks for glass, the others otherwise). */
private fun storedIconStyle(prefs: SharedPreferences?, key: String, glass: Boolean, default: IconStyle): IconStyle =
    IconStyle.entries.firstOrNull { it.name == prefs?.getString(key, null) && it.forGlass == glass } ?: default
