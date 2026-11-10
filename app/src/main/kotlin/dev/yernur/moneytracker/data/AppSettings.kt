package dev.yernur.moneytracker.data

import android.content.SharedPreferences
import dev.yernur.moneytracker.data.security.RecentsMode
import dev.yernur.moneytracker.data.security.StoredPin
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
    private val _pin = MutableStateFlow(
        prefs?.getString(PIN_HASH, null)?.let { hash -> StoredPin(hash, prefs.getString(PIN_SALT, "").orEmpty(), prefs.getInt(PIN_LENGTH, 4)) },
    )
    private val _pinFailures = MutableStateFlow((prefs?.getInt(PIN_FAILURES, 0) ?: 0) to (prefs?.getLong(PIN_LOCKED_UNTIL, 0L) ?: 0L))
    private val _biometricUnlock = MutableStateFlow(prefs?.getBoolean(BIOMETRIC_UNLOCK, false) ?: false)
    private val _autoLockSeconds = MutableStateFlow(prefs?.getInt(AUTO_LOCK_SECONDS, 30) ?: 30)
    private val _recentsMode = MutableStateFlow(
        prefs?.getString(RECENTS_MODE, null)?.let { name -> RecentsMode.entries.firstOrNull { it.name == name } ?: RecentsMode.Hide.takeIf { name == "Blur" } } ?: RecentsMode.Off,
    )
    private val _widgetGlass = MutableStateFlow(prefs?.getBoolean(WIDGET_GLASS, true) ?: true)
    private val _widgetDotNumerals = MutableStateFlow(prefs?.getBoolean(WIDGET_DOT_NUMERALS, true) ?: true)
    private val _spreadsheetId = MutableStateFlow(prefs?.getString(SPREADSHEET_ID, null))
    private val _spreadsheetUrl = MutableStateFlow(prefs?.getString(SPREADSHEET_URL, null))
    private val _keptDuplicates = MutableStateFlow(prefs?.getString(KEPT_DUPLICATES, null)?.split(',')?.filter { it.isNotEmpty() }?.toSet().orEmpty())
    private val _showAllAccounts = MutableStateFlow(prefs?.getBoolean(SHOW_ALL_ACCOUNTS, false) ?: false)
    private val _reminderEnabled = MutableStateFlow(prefs?.getBoolean(REMINDER_ENABLED, true) ?: true)
    private val _reminderMinute = MutableStateFlow(prefs?.getInt(REMINDER_MINUTE, 20 * 60) ?: (20 * 60))
    private val _walletCapture = MutableStateFlow(prefs?.getBoolean(WALLET_CAPTURE, false) ?: false)
    private val _walletAccountUid = MutableStateFlow(prefs?.getString(WALLET_ACCOUNT, null))
    private val _autoSync = MutableStateFlow(prefs?.getBoolean(AUTO_SYNC, true) ?: true)
    private val _lastSyncAt = MutableStateFlow(prefs?.getString(LAST_SYNC_AT, null))
    private val _pendingSpreadsheetId = MutableStateFlow(prefs?.getString(PENDING_SPREADSHEET_ID, null))
    private val _lastUpdateCheck = MutableStateFlow(prefs?.getLong(LAST_UPDATE_CHECK, 0L) ?: 0L)
    private val _ratesUpdatedAt = MutableStateFlow(prefs?.getLong(RATES_UPDATED_AT, 0L)?.takeIf { it > 0 })
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

    /** When (epoch millis) the bank rates were last fetched successfully; null before the first fetch. */
    val ratesUpdatedAt: StateFlow<Long?> = _ratesUpdatedAt

    fun setRatesUpdatedAt(millis: Long) {
        _ratesUpdatedAt.value = millis
        prefs?.edit()?.putLong(RATES_UPDATED_AT, millis)?.apply()
    }

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

    /** Uids of the second operation of a possible duplicate pair that the owner chose to keep ("Keep both"), so the pair is not offered again. */
    val keptDuplicates: StateFlow<Set<String>> = _keptDuplicates

    fun keepDuplicate(uid: String) {
        _keptDuplicates.value = _keptDuplicates.value + uid
        prefs?.edit()?.putString(KEPT_DUPLICATES, _keptDuplicates.value.joinToString(","))?.apply()
    }

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

    /** The owner's consent to read Google Wallet payment notifications (More -> Automation). Off until they agree. */
    val walletCapture: StateFlow<Boolean> = _walletCapture

    fun setWalletCapture(enabled: Boolean) {
        _walletCapture.value = enabled
        prefs?.edit()?.putBoolean(WALLET_CAPTURE, enabled)?.apply()
    }

    private val walletSeen = ArrayDeque(prefs?.getString(WALLET_SEEN, null)?.split(SEEN_SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty())

    /** True the first time a Wallet payment key is offered, false for a repeat (the last 100 are remembered, also across restarts). */
    @Synchronized
    fun firstSeenWalletPayment(key: String): Boolean {
        if (key in walletSeen) return false
        walletSeen.addLast(key)
        while (walletSeen.size > WALLET_SEEN_MAX) walletSeen.removeFirst()
        prefs?.edit()?.putString(WALLET_SEEN, walletSeen.joinToString(SEEN_SEPARATOR))?.apply()
        return true
    }

    /** The account Wallet payments go to; null until the owner picks one (the first payment asks). */
    val walletAccountUid: StateFlow<String?> = _walletAccountUid

    fun setWalletAccount(uid: String?) {
        _walletAccountUid.value = uid
        prefs?.edit()?.apply { if (uid != null) putString(WALLET_ACCOUNT, uid) else remove(WALLET_ACCOUNT) }?.apply()
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

    /** Widget look: glass (dark translucent) or a solid card that follows the phone's light/dark mode. */
    val widgetGlass: StateFlow<Boolean> = _widgetGlass

    fun setWidgetGlass(enabled: Boolean) {
        _widgetGlass.value = enabled
        prefs?.edit()?.putBoolean(WIDGET_GLASS, enabled)?.apply()
    }

    /** Big widget numerals in the dot-matrix font (Doto) instead of the system font. */
    val widgetDotNumerals: StateFlow<Boolean> = _widgetDotNumerals

    fun setWidgetDotNumerals(enabled: Boolean) {
        _widgetDotNumerals.value = enabled
        prefs?.edit()?.putBoolean(WIDGET_DOT_NUMERALS, enabled)?.apply()
    }

    /** The app-lock PIN (hash only), or null when there is none. */
    val pin: StateFlow<StoredPin?> = _pin

    fun setPin(value: StoredPin?) {
        _pin.value = value
        prefs?.edit()?.apply {
            if (value == null) remove(PIN_HASH).remove(PIN_SALT).remove(PIN_LENGTH)
            else putString(PIN_HASH, value.hash).putString(PIN_SALT, value.salt).putInt(PIN_LENGTH, value.length)
        }?.apply()
    }

    /** Wrong tries in a row, and the epoch millis until which the keypad refuses (0 = not waiting). */
    val pinFailures: StateFlow<Pair<Int, Long>> = _pinFailures

    fun setPinFailures(count: Int, lockedUntil: Long) {
        _pinFailures.value = count to lockedUntil
        prefs?.edit()?.putInt(PIN_FAILURES, count)?.putLong(PIN_LOCKED_UNTIL, lockedUntil)?.apply()
    }

    /** Offer fingerprint / face on the lock screen; the PIN always still works. */
    val biometricUnlock: StateFlow<Boolean> = _biometricUnlock

    fun setBiometricUnlock(enabled: Boolean) {
        _biometricUnlock.value = enabled
        prefs?.edit()?.putBoolean(BIOMETRIC_UNLOCK, enabled)?.apply()
    }

    /** How long the app may be away before it asks for the PIN again (0 = every time). */
    val autoLockSeconds: StateFlow<Int> = _autoLockSeconds

    fun setAutoLockSeconds(seconds: Int) {
        _autoLockSeconds.value = seconds
        prefs?.edit()?.putInt(AUTO_LOCK_SECONDS, seconds)?.apply()
    }

    val recentsMode: StateFlow<RecentsMode> = _recentsMode

    fun setRecentsMode(mode: RecentsMode) {
        _recentsMode.value = mode
        prefs?.edit()?.putString(RECENTS_MODE, mode.name)?.apply()
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
        val NAV_POOL = listOf("home", "history", "charts", "accounts", "analysis", "categories", "tags", "wealth")
        val NAV_DEFAULT = NAV_POOL.take(5)
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
        private const val WIDGET_GLASS = "widgetGlass"
        private const val WIDGET_DOT_NUMERALS = "widgetDotNumerals"
        private const val PIN_HASH = "pinHash"
        private const val PIN_SALT = "pinSalt"
        private const val PIN_LENGTH = "pinLength"
        private const val PIN_FAILURES = "pinFailures"
        private const val PIN_LOCKED_UNTIL = "pinLockedUntil"
        private const val BIOMETRIC_UNLOCK = "biometricUnlock"
        private const val AUTO_LOCK_SECONDS = "autoLockSeconds"
        private const val RECENTS_MODE = "recentsMode"
        private const val GOOGLE_CONNECTED = "googleConnected"
        private const val LAST_UPDATE_CHECK = "lastUpdateCheck"
        private const val LAST_RATES_DATE = "lastRatesDate"
        private const val WEALTH_CURRENCY = "wealthCurrency"
        private const val RATES_UPDATED_AT = "ratesUpdatedAt"
        private const val AVAILABLE_UPDATE = "availableUpdate"
        private const val PENDING_SPREADSHEET_ID = "pendingSpreadsheetId"
        private const val SPREADSHEET_ID = "spreadsheetId"
        private const val SPREADSHEET_URL = "spreadsheetUrl"
        private const val DEVICE_ID = "deviceId"
        private const val SHOW_ALL_ACCOUNTS = "showAllAccounts"
        private const val KEPT_DUPLICATES = "keptDuplicates"
        private const val REMINDER_ENABLED = "reminderEnabled"
        private const val REMINDER_MINUTE = "reminderMinute"
        private const val WALLET_CAPTURE = "walletCapture"
        private const val WALLET_ACCOUNT = "walletAccountUid"
        private const val WALLET_SEEN = "walletSeen"
        private const val WALLET_SEEN_MAX = 100
        private const val SEEN_SEPARATOR = "\u0001"
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
