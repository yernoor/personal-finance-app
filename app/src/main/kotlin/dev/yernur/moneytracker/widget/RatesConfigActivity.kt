package dev.yernur.moneytracker.widget

import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import dev.yernur.moneytracker.MoneyApp
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
            val container = (application as MoneyApp).container
            val choices = rateCurrencyChoices(container.fx.rates.first())
            val picked = RateCurrencies.of(this@RatesConfigActivity).get(appWidgetId).filter { it in choices }.toMutableSet()
            AlertDialog.Builder(this@RatesConfigActivity)
                .setTitle("Rates: up to $MAX_RATE_CURRENCIES currencies")
                .setMultiChoiceItems(choices.toTypedArray(), BooleanArray(choices.size) { choices[it] in picked }) { dialog, which, checked ->
                    when {
                        !checked -> picked.remove(choices[which])
                        picked.size >= MAX_RATE_CURRENCIES -> {
                            (dialog as AlertDialog).listView.setItemChecked(which, false)
                            Toast.makeText(this@RatesConfigActivity, "At most $MAX_RATE_CURRENCIES", Toast.LENGTH_SHORT).show()
                        }
                        else -> picked.add(choices[which])
                    }
                }
                .setPositiveButton("Done") { _, _ -> choose(appWidgetId, choices.filter { it in picked }) }
                .setNegativeButton("Cancel") { _, _ -> finish() }
                .setOnCancelListener { finish() }
                .show()
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
