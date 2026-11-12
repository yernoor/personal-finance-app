package dev.yernur.moneytracker.widget

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** What one Quick add widget opens. [id] is the value MainActivity reads from EXTRA_KIND. */
enum class QuickAddKind(val id: String, val label: String, val button: String) {
    Expense("expense", "Expense", "Add expense"),
    Income("income", "Income", "Add income"),
    Transfer("transfer", "Transfer", "Add transfer");

    companion object {
        fun byId(id: String?) = entries.firstOrNull { it.id == id }
    }
}

/** The kind chosen for each placed Quick add widget, keyed by its appWidgetId. A widget with no choice (placed before this existed) is an expense one. */
class QuickAddKinds(private val prefs: SharedPreferences) {
    fun get(appWidgetId: Int): QuickAddKind = QuickAddKind.byId(prefs.getString(appWidgetId.toString(), null)) ?: QuickAddKind.Expense

    fun set(appWidgetId: Int, kind: QuickAddKind) = prefs.edit().putString(appWidgetId.toString(), kind.id).apply()

    fun remove(appWidgetId: Int) = prefs.edit().remove(appWidgetId.toString()).apply()

    /** The kind now and after every change, so a placed widget redraws when the config screen saves (see [observe]). */
    fun changes(appWidgetId: Int): Flow<QuickAddKind> = prefs.observe(appWidgetId.toString()) { get(appWidgetId) }

    companion object {
        fun of(context: Context) = QuickAddKinds(context.applicationContext.getSharedPreferences("quickadd", Context.MODE_PRIVATE))
    }
}

/**
 * The value now and after every change of [key]. A Glance session runs provideGlance once, so a widget that read its setting there kept the old
 * one when the config screen saved a new choice while the session was alive (placement starts it); widgets read this inside their composition.
 */
fun <T> SharedPreferences.observe(key: String, read: () -> T): Flow<T> = callbackFlow {
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed -> if (changed == key) trySend(read()) }
    registerOnSharedPreferenceChangeListener(listener)
    send(read())
    awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
}.distinctUntilChanged()
