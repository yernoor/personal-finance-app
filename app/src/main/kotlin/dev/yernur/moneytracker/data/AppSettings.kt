package dev.yernur.moneytracker.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

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
    private val _iconStyle = MutableStateFlow(storedIconStyle(prefs, ICON_STYLE, glass = false, IconStyle.Springboard))
    private val _glassIconStyle = MutableStateFlow(storedIconStyle(prefs, GLASS_ICON_STYLE, glass = true, IconStyle.LiquidTile))
    private val _spreadsheetId = MutableStateFlow(prefs?.getString(SPREADSHEET_ID, null))
    private val _spreadsheetUrl = MutableStateFlow(prefs?.getString(SPREADSHEET_URL, null))
    private val _showAllAccounts = MutableStateFlow(prefs?.getBoolean(SHOW_ALL_ACCOUNTS, false) ?: false)
    private val _reminderEnabled = MutableStateFlow(prefs?.getBoolean(REMINDER_ENABLED, true) ?: true)
    private val _reminderMinute = MutableStateFlow(prefs?.getInt(REMINDER_MINUTE, 20 * 60) ?: (20 * 60))
    private val _autoSync = MutableStateFlow(prefs?.getBoolean(AUTO_SYNC, true) ?: true)
    private val _lastSyncAt = MutableStateFlow(prefs?.getString(LAST_SYNC_AT, null))
    private val _pendingSpreadsheetId = MutableStateFlow(prefs?.getString(PENDING_SPREADSHEET_ID, null))
    private val _lastUpdateCheck = MutableStateFlow(prefs?.getLong(LAST_UPDATE_CHECK, 0L) ?: 0L)
    private val _wealthCurrency = MutableStateFlow(prefs?.getString(WEALTH_CURRENCY, null) ?: DEFAULT_CURRENCY)
    private val _lastRatesDate = MutableStateFlow(prefs?.getString(LAST_RATES_DATE, null))
    private val _availableUpdate = MutableStateFlow(prefs?.getString(AVAILABLE_UPDATE, null))
    private val _googleConnected = MutableStateFlow(prefs?.getBoolean(GOOGLE_CONNECTED, false) ?: false)
    private val _glass = MutableStateFlow(prefs?.getBoolean(GLASS, false) ?: false)
    private val warned = HashSet<String>(prefs?.getStringSet(WARNED, emptySet()).orEmpty())

    /** Warn when a projection passes this percent of a category limit (categories can override). */
    val warnThreshold: StateFlow<Int> = _warnThreshold
    val notifications: StateFlow<Boolean> = _notifications

    /** What the History tab opens on. The day by default. */
    val historyPeriod: StateFlow<PeriodUnit> = _historyPeriod

    /** The spreadsheet this app created in Google Drive (only files the app created are reachable, by the drive.file scope). */
    val spreadsheetId: StateFlow<String?> = _spreadsheetId
    val spreadsheetUrl: StateFlow<String?> = _spreadsheetUrl

    /** The owner approved Google access (step 1). Only a hint for the screen: the permission itself lives with Google and can be revoked. */
    val googleConnected: StateFlow<Boolean> = _googleConnected

    fun setGoogleConnected(connected: Boolean) {
        _googleConnected.value = connected
        prefs?.edit()?.putBoolean(GOOGLE_CONNECTED, connected)?.apply()
    }

    /** When the app last asked GitHub for a newer release (epoch millis), so it asks by itself at most once a day. */
    val lastUpdateCheck: StateFlow<Long> = _lastUpdateCheck

    /** The newest release tag found by the last check (e.g. "v0.3.0"), shown on the More screen when it is newer than the installed version. */
    val availableUpdate: StateFlow<String?> = _availableUpdate

    /** The currency the Wealth and Compound interest screens show (the owner's switch); stored amounts stay in [DEFAULT_CURRENCY]. */
    val wealthCurrency: StateFlow<String> = _wealthCurrency

    fun setWealthCurrency(code: String) {
        _wealthCurrency.value = code
        prefs?.edit()?.putString(WEALTH_CURRENCY, code)?.apply()
    }

    /** The day (YYYY-MM-DD, device local) the app last asked the National Bank for rates: it asks once a day. */
    val lastRatesDate: StateFlow<String?> = _lastRatesDate

    fun setLastRatesDate(date: String) {
        _lastRatesDate.value = date
        prefs?.edit()?.putString(LAST_RATES_DATE, date)?.apply()
    }

    fun setLastUpdateCheck(millis: Long) {
        _lastUpdateCheck.value = millis
        prefs?.edit()?.putLong(LAST_UPDATE_CHECK, millis)?.apply()
    }

    fun setAvailableUpdate(tag: String?) {
        _availableUpdate.value = tag
        prefs?.edit()?.apply { if (tag != null) putString(AVAILABLE_UPDATE, tag) else remove(AVAILABLE_UPDATE) }?.apply()
    }

    /** Show the "All accounts" figure under the balance on Home. Hidden by default. */
    val showAllAccounts: StateFlow<Boolean> = _showAllAccounts

    fun setShowAllAccounts(on: Boolean) {
        _showAllAccounts.value = on
        prefs?.edit()?.putBoolean(SHOW_ALL_ACCOUNTS, on)?.apply()
    }

    /** Daily "add your expenses" reminder, at [reminderMinute] minutes after local midnight (default 20:00). */
    val reminderEnabled: StateFlow<Boolean> = _reminderEnabled
    val reminderMinute: StateFlow<Int> = _reminderMinute

    fun setReminder(enabled: Boolean, minuteOfDay: Int) {
        _reminderEnabled.value = enabled
        _reminderMinute.value = minuteOfDay.coerceIn(0, 24 * 60 - 1)
        prefs?.edit()?.putBoolean(REMINDER_ENABLED, enabled)?.putInt(REMINDER_MINUTE, _reminderMinute.value)?.apply()
    }

    /** Sync with the sheet once a week in the background (only matters while a sheet is linked). */
    val autoSync: StateFlow<Boolean> = _autoSync

    fun setAutoSync(on: Boolean) {
        _autoSync.value = on
        prefs?.edit()?.putBoolean(AUTO_SYNC, on)?.apply()
    }

    /** UTC time the last successful Sync now finished; phone rows edited after it are not overwritten by the next pull. */
    val lastSyncAt: StateFlow<String?> = _lastSyncAt

    fun setLastSyncAt(stamp: String?) {
        _lastSyncAt.value = stamp
        prefs?.edit()?.apply { if (stamp != null) putString(LAST_SYNC_AT, stamp) else remove(LAST_SYNC_AT) }?.apply()
    }

    /** A spreadsheet created by a push that has not finished: the next push fills this one instead of making another. */
    val pendingSpreadsheetId: StateFlow<String?> = _pendingSpreadsheetId

    fun setPendingSpreadsheet(id: String?) {
        _pendingSpreadsheetId.value = id
        prefs?.edit()?.apply { if (id != null) putString(PENDING_SPREADSHEET_ID, id) else remove(PENDING_SPREADSHEET_ID) }?.apply()
    }

    fun setSpreadsheet(id: String, url: String) {
        _spreadsheetId.value = id
        _spreadsheetUrl.value = url
        prefs?.edit()?.putString(SPREADSHEET_ID, id)?.putString(SPREADSHEET_URL, url)?.apply()
    }

    /** Forgets the link; the spreadsheet itself stays in Drive. */
    fun forgetSpreadsheet() {
        _spreadsheetId.value = null
        _spreadsheetUrl.value = null
        _lastSyncAt.value = null
        prefs?.edit()?.remove(SPREADSHEET_ID)?.remove(SPREADSHEET_URL)?.remove(LAST_SYNC_AT)?.apply()
    }

    /** Identifies this phone in the sheet's `_meta` tab; created once and kept. */
    @Synchronized fun deviceId(): String {
        deviceId?.let { return it }
        val stored = prefs?.getString(DEVICE_ID, null)
        val id = stored ?: UUID.randomUUID().toString().also { prefs?.edit()?.putString(DEVICE_ID, it)?.apply() }
        deviceId = id
        return id
    }

    private var deviceId: String? = null

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

    /** Ids of the cards on Home, in order. The default shows the monthly totals. */
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
        private const val GOOGLE_CONNECTED = "googleConnected"
        private const val LAST_UPDATE_CHECK = "lastUpdateCheck"
        private const val LAST_RATES_DATE = "lastRatesDate"
        private const val WEALTH_CURRENCY = "wealthCurrency"
        private const val AVAILABLE_UPDATE = "availableUpdate"
        private const val PENDING_SPREADSHEET_ID = "pendingSpreadsheetId"
        private const val SPREADSHEET_ID = "spreadsheetId"
        private const val SPREADSHEET_URL = "spreadsheetUrl"
        private const val DEVICE_ID = "deviceId"
        private const val SHOW_ALL_ACCOUNTS = "showAllAccounts"
        private const val REMINDER_ENABLED = "reminderEnabled"
        private const val REMINDER_MINUTE = "reminderMinute"
        private const val AUTO_SYNC = "autoSync"
        private const val LAST_SYNC_AT = "lastSyncAt"
        private const val ICON_STYLE = "iconStyle"
        private const val GLASS_ICON_STYLE = "glassIconStyle"
        private const val HOME_CARDS = "homeCards"
        private const val HOME_PERIOD = "homeTotalsPeriod"
    }
}

/** A stored style, accepted only if it belongs to the right set (glass looks for glass, the others otherwise). */
private fun storedIconStyle(prefs: SharedPreferences?, key: String, glass: Boolean, default: IconStyle): IconStyle =
    IconStyle.entries.firstOrNull { it.name == prefs?.getString(key, null) && it.forGlass == glass } ?: default
