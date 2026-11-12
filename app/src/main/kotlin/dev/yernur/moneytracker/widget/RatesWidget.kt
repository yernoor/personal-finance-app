package dev.yernur.moneytracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.yernur.moneytracker.MoneyApp
import dev.yernur.moneytracker.R
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Up to three currencies against tenge (National Bank rates, from the cache) and a refresh button. The currencies are chosen when it is placed. */
class RatesWidget : MoneyWidget(2, 2) {
    @Composable
    override fun Content(context: Context, data: WidgetData, palette: WidgetPalette, dot: Boolean) =
        RatesCard(context, ratesRows(listOf("USD", "EUR", "RUB"), SAMPLE_RATES), "Rates", palette)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as MoneyApp).container
        val saved = RateCurrencies.of(context)
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val rates = container.fx.rates.first()
        val title = ratesTitle(container.settings.ratesUpdatedAt.value)
        val style = container.settings.widgetStyle.value
        provideContent {
            val codes by remember { saved.changes(appWidgetId) }.collectAsState(saved.get(appWidgetId))
            GlanceTheme { RatesCard(context, ratesRows(codes, rates), title, widgetPalette(context, style)) }
        }
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        RateCurrencies.of(context).remove(GlanceAppWidgetManager(context).getAppWidgetId(glanceId))
    }
}

/** Fictional rates for the picker preview. */
private val SAMPLE_RATES = mapOf("USD" to 450.0, "EUR" to 505.0, "RUB" to 5.5)

@Composable
private fun RatesCard(context: Context, rows: List<RateRow>, title: String, palette: WidgetPalette) {
    WidgetCard(palette, openApp(context)) {
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label(title, palette, size = 11)
                Spacer(GlanceModifier.defaultWeight())
                Box(GlanceModifier.size(28.dp).clickable(actionRunCallback<RefreshRatesAction>()), contentAlignment = Alignment.Center) {
                    Image(ImageProvider(R.drawable.widget_refresh), "Refresh rates", GlanceModifier.size(18.dp), colorFilter = ColorFilter.tint(palette.primary))
                }
            }
            // Fewer currencies get bigger type and spread over the card instead of leaving it half empty.
            val (codeSize, rateSize) = when (rows.size) { 1 -> 14 to 30; 2 -> 12 to 22; else -> 11 to 14 }
            Spacer(GlanceModifier.defaultWeight())
            rows.forEach { row ->
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Label(row.code, palette, size = codeSize)
                    Spacer(GlanceModifier.defaultWeight())
                    Text(row.text, style = TextStyle(color = palette.primary, fontSize = rateSize.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                }
                Spacer(GlanceModifier.defaultWeight())
            }
        }
    }
}

/** The refresh button: asks the bank (at most once an hour, see FxRepository.refreshNow), then redraws every widget. */
class RefreshRatesAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val container = (context.applicationContext as MoneyApp).container
        runCatching { container.fx.refreshNow(LocalDate.now()) }
        updateAllWidgets(context)
    }
}
