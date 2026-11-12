package dev.yernur.moneytracker.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
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
abstract class MoneyWidget(private val cellsWide: Int, private val cellsHigh: Int) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    /** The picker draws the preview at the widget's default size (cells -> dp: 73n-16 wide, 74m-16 high; the docs' 118 dp rows drew the previews too tall on the emulator), not the minimum one. */
    override val previewSizeMode = SizeMode.Responsive(setOf(DpSize((73 * cellsWide - 16).dp, (74 * cellsHigh - 16).dp)))

    @Composable
    abstract fun Content(context: Context, data: WidgetData, palette: WidgetPalette, dot: Boolean)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as MoneyApp).container
        val data = loadWidgetData(container.db, container.forecasts)
        val style = container.settings.widgetStyle.value
        provideContent { GlanceTheme { Content(context, data, widgetPalette(context, style), style.dotNumerals) } }
    }

    /** Android 15+: the picker shows the real widget, drawn with fictional numbers in the look the owner chose. */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val container = (context.applicationContext as MoneyApp).container
        val style = container.settings.widgetStyle.value
        provideContent { GlanceTheme { Content(context, sampleWidgetData(), widgetPalette(context, style), style.dotNumerals) } }
    }
}

private fun WidgetData.monthName() = month.month.getDisplayName(DateTextStyle.FULL, Locale.getDefault())

/** The 2x2 minimum (70n - 30 dp) less the 14 dp card padding on each side. */
private const val MONTH_CONTENT_WIDTH_DP = 110f - 28f

class MonthWidget : MoneyWidget(2, 2) {
    @Composable
    override fun Content(context: Context, data: WidgetData, palette: WidgetPalette, dot: Boolean) {
        WidgetCard(palette, openApp(context)) {
            Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Vertical.Bottom) {
                Label("Spent in ${data.monthName()}", palette)
                Spacer(GlanceModifier.defaultWeight())
                BigNumber(context, wholeUnits(data.monthSpent), palette, dot, sizeDp = 24, maxWidthDp = MONTH_CONTENT_WIDTH_DP)
                Label("₸ · on pace for ${wholeUnits(data.projected)}", palette, accent = true, maxLines = 2)
            }
        }
    }
}

class TodayWidget : MoneyWidget(2, 2) {
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

/** One button that opens the add screen of the kind chosen when the widget was placed (stored per widget id in [QuickAddKinds]). */
class QuickAddWidget : MoneyWidget(2, 1) {
    @Composable
    override fun Content(context: Context, data: WidgetData, palette: WidgetPalette, dot: Boolean) = QuickAddButton(context, QuickAddKind.Expense, palette)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val kind = QuickAddKinds.of(context).get(GlanceAppWidgetManager(context).getAppWidgetId(id))
        val style = (context.applicationContext as MoneyApp).container.settings.widgetStyle.value
        provideContent { GlanceTheme { QuickAddButton(context, kind, widgetPalette(context, style)) } }
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        QuickAddKinds.of(context).remove(GlanceAppWidgetManager(context).getAppWidgetId(glanceId))
    }
}

@Composable
private fun QuickAddButton(context: Context, kind: QuickAddKind, palette: WidgetPalette) {
    Box(GlanceModifier.fillMaxSize().background(palette.background).cornerRadius(26.dp).padding(8.dp)) {
        Box(
            GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_button)).cornerRadius(18.dp).clickable(openApp(context, kind = kind.id)),
            contentAlignment = Alignment.Center,
        ) { Text(kind.button, style = TextStyle(color = palette.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1) }
    }
}

class BudgetWidget : MoneyWidget(4, 4) {
    @Composable
    override fun Content(context: Context, data: WidgetData, palette: WidgetPalette, dot: Boolean) {
        WidgetCard(palette, openApp(context)) {
            Column(GlanceModifier.fillMaxSize()) {
                Label("Spent in ${data.monthName()}", palette)
                BigNumber(context, wholeUnits(data.monthSpent), palette, dot)
                Label("₸ · on pace for ${wholeUnits(data.projected)}", palette, accent = true)
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

class DashboardWidget : MoneyWidget(4, 6) {
    @Composable
    override fun Content(context: Context, data: WidgetData, palette: WidgetPalette, dot: Boolean) {
        WidgetCard(palette, openApp(context)) {
            Column(GlanceModifier.fillMaxSize()) {
                Label("Spent in ${data.monthName()}", palette)
                BigNumber(context, wholeUnits(data.monthSpent), palette, dot)
                Label("₸ · on pace for ${wholeUnits(data.projected)} · today ${wholeUnits(data.todaySpent)}", palette, accent = true)
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
class RatesWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = RatesWidget() }
class BudgetWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = BudgetWidget() }
class DashboardWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = DashboardWidget() }

/** Every widget with the receiver that hosts it. */
private val WIDGETS: List<Pair<MoneyWidget, Class<*>>> = listOf(
    MonthWidget() to MonthWidgetReceiver::class.java,
    TodayWidget() to TodayWidgetReceiver::class.java,
    QuickAddWidget() to QuickAddWidgetReceiver::class.java,
    RatesWidget() to RatesWidgetReceiver::class.java,
    BudgetWidget() to BudgetWidgetReceiver::class.java,
    DashboardWidget() to DashboardWidgetReceiver::class.java,
)

/**
 * Redraws every placed widget; call after anything that changes spending or the widget look.
 * Ids come from Android, per receiver, not from Glance's `getGlanceIds` / `updateAll`: in a release build R8 merged all the widget classes into one,
 * so Glance thought every placed widget belonged to every class and drew Month onto the Quick add widget (reproduced on the emulator, docs/QUICK_ADD_BUG_PLAN.md).
 */
suspend fun updateAllWidgets(context: Context) {
    val glance = GlanceAppWidgetManager(context)
    val system = AppWidgetManager.getInstance(context)
    WIDGETS.forEach { (widget, receiver) ->
        system.getAppWidgetIds(ComponentName(context, receiver)).forEach { widget.update(context, glance.getGlanceIdBy(it)) }
    }
}

/** Fills the picker with the Android 15+ generated previews; the system rate-limits this, so call it at launch and when the look changes only. */
suspend fun updateWidgetPreviews(context: Context) {
    if (Build.VERSION.SDK_INT < 35) return
    val manager = GlanceAppWidgetManager(context)
    runCatching { setAllPreviews(manager) } // best effort: a missing preview only means the picker shows the app icon
}

private suspend fun setAllPreviews(manager: GlanceAppWidgetManager) {
    listOf(
        MonthWidgetReceiver::class, TodayWidgetReceiver::class, QuickAddWidgetReceiver::class, RatesWidgetReceiver::class,
        BudgetWidgetReceiver::class, DashboardWidgetReceiver::class,
    ).forEach { manager.setWidgetPreviews(it) }
}
