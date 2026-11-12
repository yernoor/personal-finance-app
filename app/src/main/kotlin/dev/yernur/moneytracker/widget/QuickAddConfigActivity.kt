package dev.yernur.moneytracker.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import dev.yernur.moneytracker.ui.AppTheme
import kotlinx.coroutines.launch

/**
 * Opens when a Quick add widget is placed (and from the launcher's reconfigure action): asks which kind of entry it should open.
 * Backing out cancels the placement, as Android expects (RESULT_CANCELED with the widget id).
 */
class QuickAddConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()
        val initial = QuickAddKinds.of(this).get(appWidgetId)
        setContent { AppTheme { QuickAddConfigSheet(initial, onCancel = ::finish) { choose(appWidgetId, it) } } }
    }

    private fun choose(appWidgetId: Int, kind: QuickAddKind) {
        QuickAddKinds.of(this).set(appWidgetId, kind)
        val context = applicationContext
        // The widget is not drawn until this returns RESULT_OK, so draw it with the new kind ourselves.
        lifecycleScope.launch { runCatching { QuickAddWidget().update(context, GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)) } }
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }
}
