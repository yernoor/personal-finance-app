package dev.yernur.moneytracker.widget

import android.content.Context
import android.content.SharedPreferences

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

    companion object {
        fun of(context: Context) = QuickAddKinds(context.applicationContext.getSharedPreferences("quickadd", Context.MODE_PRIVATE))
    }
}
