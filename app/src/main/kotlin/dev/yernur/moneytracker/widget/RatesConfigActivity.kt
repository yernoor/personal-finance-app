package dev.yernur.moneytracker.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import dev.yernur.moneytracker.MoneyApp
import dev.yernur.moneytracker.ui.AppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Opens when a Rates widget is placed (and from the launcher's reconfigure action): pick up to three currencies.
 * Backing out cancels the placement (RESULT_CANCELED with the widget id), as Android expects.
 */
class RatesConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()
        lifecycleScope.launch {
            val rates = (application as MoneyApp).container.fx.rates.first()
            val choices = rateCurrencyChoices(rates)
            val initial = RateCurrencies.of(this@RatesConfigActivity).get(appWidgetId).toSet()
            setContent { AppTheme { RatesConfigSheet(choices, rates, initial, onCancel = ::finish) { choose(appWidgetId, it) } } }
        }
    }

    private fun choose(appWidgetId: Int, codes: List<String>) {
        if (codes.isEmpty()) return finish() // nothing picked: same as cancelling
        RateCurrencies.of(this).set(appWidgetId, codes)
        val context = applicationContext
        // The widget is not drawn until this returns RESULT_OK, so draw it with the new currencies ourselves.
        lifecycleScope.launch { runCatching { RatesWidget().update(context, GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)) } }
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }
}
