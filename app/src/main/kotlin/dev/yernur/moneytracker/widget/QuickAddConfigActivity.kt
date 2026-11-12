package dev.yernur.moneytracker.widget

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Opens when a Quick add widget is placed (and from the launcher's reconfigure action): asks which kind of entry it should open.
 * Backing out cancels the placement, as Android expects (RESULT_CANCELED with the widget id).
 */
class QuickAddConfigActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()
        val kinds = QuickAddKind.entries
        AlertDialog.Builder(this)
            .setTitle("Quick add: what to add?")
            .setItems(kinds.map { it.label }.toTypedArray()) { _, which -> choose(appWidgetId, kinds[which]) }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun choose(appWidgetId: Int, kind: QuickAddKind) {
        QuickAddKinds.of(this).set(appWidgetId, kind)
        val context = applicationContext
        // The widget is not drawn until this returns RESULT_OK, so draw it with the new kind ourselves.
        CoroutineScope(Dispatchers.Default).launch {
            runCatching { QuickAddWidget().update(context, GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)) }
        }
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }
}
