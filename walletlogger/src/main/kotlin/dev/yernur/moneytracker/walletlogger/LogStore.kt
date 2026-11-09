package dev.yernur.moneytracker.walletlogger

import android.content.Context
import java.io.File

/** Local storage only: a JSON-lines file for captured notifications, preferences for the allow-list and the seen package names. */
class LogStore(context: Context, private val maxEntries: Int = 2000) {
    private val file = File(context.filesDir, "notifications.jsonl")
    private val prefs = context.getSharedPreferences("walletlogger", Context.MODE_PRIVATE)

    /** Packages whose notification content may be read. Default: Google Wallet (package name unverified on the phone). */
    fun allowedPackages(): Set<String> = prefs.getStringSet(KEY_ALLOWED, null) ?: setOf(WALLET_PACKAGE)

    fun setAllowedPackages(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_ALLOWED, packages).apply()
    }

    fun isAllowed(packageName: String): Boolean = packageName in allowedPackages()

    /** Package name only, never content: lets the owner find the real package of an app. Counts are per package. */
    @Synchronized
    fun noteSeen(packageName: String) {
        val seen = seenPackages().toMutableMap()
        seen[packageName] = (seen[packageName] ?: 0) + 1
        prefs.edit().putStringSet(KEY_SEEN, seen.map { "${it.key}|${it.value}" }.toSet()).apply()
    }

    fun seenPackages(): Map<String, Int> = (prefs.getStringSet(KEY_SEEN, null) ?: emptySet()).associate {
        it.substringBeforeLast('|') to it.substringAfterLast('|').toInt()
    }

    @Synchronized
    fun append(entry: LoggedNotification) {
        val lines = readLines() + entry.toJson()
        file.writeText(lines.takeLast(maxEntries).joinToString("\n", postfix = "\n"))
    }

    @Synchronized
    fun entries(): List<LoggedNotification> = readLines().map { LoggedNotification.fromJson(it) }

    /** The whole log as the text the Export button writes. */
    fun exportText(): String = readLines().joinToString("\n", postfix = "\n")

    @Synchronized
    fun clear() {
        file.delete()
        prefs.edit().remove(KEY_SEEN).apply()
    }

    private fun readLines(): List<String> = if (file.exists()) file.readLines().filter { it.isNotBlank() } else emptyList()

    companion object {
        const val WALLET_PACKAGE = "com.google.android.apps.walletnfcrel"
        private const val KEY_ALLOWED = "allowed"
        private const val KEY_SEEN = "seen"
    }
}
