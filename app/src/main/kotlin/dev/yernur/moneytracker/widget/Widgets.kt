package dev.yernur.moneytracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.sp
import dev.yernur.moneytracker.MoneyApp
import dev.yernur.moneytracker.R
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

/** Loads the numbers and the look once per refresh, then hands them to [content]. */
abstract class MoneyWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    @Composable
    abstract fun Content(context: Context, data: WidgetData, palette: WidgetPalette, dot: Boolean)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as MoneyApp).container
        val data = loadWidgetData(container.db, container.forecasts)
        val palette = WidgetPalette(container.settings.widgetGlass.value)
        val dot = container.settings.widgetDotNumerals.value
        provideContent { Content(context, data, palette, dot) }
    }
}

private fun WidgetData.monthName() = month.month.getDisplayName(DateTextStyle.FULL, Locale.getDefault())

class MonthWidget : MoneyWidget() {
    @Composable
    override fun Content(context: Context, data: WidgetData, palette: WidgetPalette, dot: Boolean) {
        WidgetCard(palette, openApp(context)) {
            Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Vertical.Bottom) {
                Label("Spent in ${data.monthName()}", palette)
                Spacer(GlanceModifier.defaultWeight())
                BigNumber(context, wholeUnits(data.monthSpent), palette, dot)
                Label("₸ · on pace for ${wholeUnits(data.projected)}", palette, warn = data.overLimit)
            }
        }
    }
}

class TodayWidget : MoneyWidget() {
    @Composable
    override fun Content(context: Context, data: WidgetData, palette: WidgetPalette, dot: Boolean) {
        WidgetCard(palette, openApp(context)) {
            Column(GlanceModifier.fillMaxSize()) {
                Label("Today", palette)
                Spacer(GlanceModifier.defaultWeight())
                BigNumber(context, wholeUnits(data.todaySpent), palette, dot)
                Label("₸ · ${data.todayCount} operation" + if (data.todayCount == 1) "" else "s", palette)
            }
        }
    }
}

class QuickAddWidget : MoneyWidget() {
    @Composable
    override fun Content(context: Context, data: WidgetData, palette: WidgetPalette, dot: Boolean) {
        Box(GlanceModifier.fillMaxSize().background(palette.background).cornerRadius(26.dp).padding(10.dp)) {
            Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                listOf("Expense" to "expense", "Income" to "income", "Transfer" to "transfer").forEachIndexed { i, (label, kind) ->
                    if (i > 0) Spacer(GlanceModifier.width(8.dp))
                    Box(
                        GlanceModifier.defaultWeight().fillMaxSize().background(ImageProvider(R.drawable.widget_button)).cornerRadius(18.dp).clickable(openApp(context, kind = kind)),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, style = TextStyle(color = palette.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1) }
                }
            }
        }
    }
}

class LimitsWidget : MoneyWidget() {
    @Composable
    override fun Content(context: Context, data: WidgetData, palette: WidgetPalette, dot: Boolean) {
        WidgetCard(palette, openApp(context)) {
            Column(GlanceModifier.fillMaxSize()) {
                Label("Limits this month", palette, warn = data.overLimit)
                Spacer(GlanceModifier.height(4.dp))
                if (data.limits.isEmpty()) Label("No category has a limit yet", palette)
                data.limits.take(3).forEach { LimitRow(it, palette) }
            }
        }
    }
}

class BudgetWidget : MoneyWidget() {
    @Composable
    override fun Content(context: Context, data: WidgetData, palette: WidgetPalette, dot: Boolean) {
        WidgetCard(palette, openApp(context)) {
            Column(GlanceModifier.fillMaxSize()) {
                Label("Spent in ${data.monthName()}", palette)
                BigNumber(context, wholeUnits(data.monthSpent), palette, dot)
                Label("₸ · on pace for ${wholeUnits(data.projected)}", palette, warn = data.overLimit)
                Spacer(GlanceModifier.height(10.dp))
                BarImage(WidgetBitmaps.stackedBar(data.topCategories.take(6).map { it.spent }, track = palette.bitmapTrack), 10)
                Spacer(GlanceModifier.height(8.dp))
                data.topCategories.take(5).forEachIndexed { i, c ->
                    Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(ImageProvider(WidgetBitmaps.progressBar(1f, WidgetBitmaps.SEGMENTS[i % WidgetBitmaps.SEGMENTS.size], 16, 16)), null, GlanceModifier.width(8.dp).height(8.dp))
                        Spacer(GlanceModifier.width(8.dp))
                        Text(c.title, style = TextStyle(color = palette.primary, fontSize = 13.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                        Text(wholeUnits(c.spent), style = TextStyle(color = palette.primary, fontSize = 13.sp), maxLines = 1)
                    }
                }
            }
        }
    }
}

class RecentWidget : MoneyWidget() {
    @Composable
    override fun Content(context: Context, data: WidgetData, palette: WidgetPalette, dot: Boolean) {
        WidgetCard(palette, openApp(context)) {
            Column(GlanceModifier.fillMaxSize()) {
                Label("Recent operations", palette)
                Spacer(GlanceModifier.height(4.dp))
                if (data.recent.isEmpty()) Label("Nothing yet", palette)
                data.recent.take(8).forEach { OperationRow(context, it, palette) }
            }
        }
    }
}

class DashboardWidget : MoneyWidget() {
    @Composable
    override fun Content(context: Context, data: WidgetData, palette: WidgetPalette, dot: Boolean) {
        WidgetCard(palette, openApp(context)) {
            Column(GlanceModifier.fillMaxSize()) {
                Label("Spent in ${data.monthName()}", palette)
                BigNumber(context, wholeUnits(data.monthSpent), palette, dot)
                Label("₸ · on pace for ${wholeUnits(data.projected)} · today ${wholeUnits(data.todaySpent)}", palette, warn = data.overLimit)
                Spacer(GlanceModifier.height(8.dp))
                BarImage(WidgetBitmaps.stackedBar(data.topCategories.take(6).map { it.spent }, track = palette.bitmapTrack), 10)
                Spacer(GlanceModifier.height(8.dp))
                data.limits.take(3).forEach { LimitRow(it, palette) }
                Spacer(GlanceModifier.height(8.dp))
                Label("Recent", palette)
                data.recent.take(5).forEach { OperationRow(context, it, palette) }
            }
        }
    }
}

class MonthWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = MonthWidget() }
class TodayWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = TodayWidget() }
class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = QuickAddWidget() }
class LimitsWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = LimitsWidget() }
class BudgetWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = BudgetWidget() }
class RecentWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = RecentWidget() }
class DashboardWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = DashboardWidget() }

/** Redraws every placed widget; call after anything that changes spending or the widget look. */
suspend fun updateAllWidgets(context: Context) {
    listOf(MonthWidget(), TodayWidget(), QuickAddWidget(), LimitsWidget(), BudgetWidget(), RecentWidget(), DashboardWidget()).forEach { it.updateAll(context) }
}
