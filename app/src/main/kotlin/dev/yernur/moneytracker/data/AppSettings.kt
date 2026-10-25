package dev.yernur.moneytracker.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** User preferences. [prefs] null keeps everything in memory (tests). */
class AppSettings(private val prefs: SharedPreferences? = null) {
    private val _warnThreshold = MutableStateFlow(prefs?.getInt(WARN_THRESHOLD, DEFAULT_THRESHOLD) ?: DEFAULT_THRESHOLD)
    private val _notifications = MutableStateFlow(prefs?.getBoolean(NOTIFICATIONS, true) ?: true)
    private val warned = HashSet<String>(prefs?.getStringSet(WARNED, emptySet()).orEmpty())

    /** Warn when a projection passes this percent of a category limit (categories can override). */
    val warnThreshold: StateFlow<Int> = _warnThreshold
    val notifications: StateFlow<Boolean> = _notifications

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
        private const val WARN_THRESHOLD = "warnThreshold"
        private const val NOTIFICATIONS = "notifications"
        private const val WARNED = "warned"
    }
}
