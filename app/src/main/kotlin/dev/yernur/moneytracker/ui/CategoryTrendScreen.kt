package dev.yernur.moneytracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack

import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.yernur.moneytracker.data.CategoryDetail
import dev.yernur.moneytracker.data.DEFAULT_CURRENCY
import dev.yernur.moneytracker.data.DETAIL_MONTHS
import dev.yernur.moneytracker.data.ForecastRepository
import dev.yernur.moneytracker.data.Money
import dev.yernur.moneytracker.data.PeriodUnit
import dev.yernur.moneytracker.data.categoryDetail
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.CategoryEntity
import dev.yernur.moneytracker.data.lastRanges
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

private fun money(minor: Long) = Money.formatRounded(minor, DEFAULT_CURRENCY)

class CategoryTrendViewModel(db: AppDatabase, forecasts: ForecastRepository, uid: String, today: LocalDate = LocalDate.now()) : ViewModel() {
    val state: StateFlow<Pair<CategoryEntity?, CategoryDetail>?> = run {
        val from = PeriodUnit.Month.lastRanges(today, DETAIL_MONTHS).first().from.toString()
        combine(
            db.transactionDao().observeLite(from, today.toString()), db.transactionDao().observeDetail(from, today.toString()),
            forecasts.observe(), db.categoryDao().observeAll(),
        ) { rows, details, forecast, categories ->
            categories.firstOrNull { it.uid == uid } to categoryDetail(rows, details, uid, forecast, today)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryTrendScreen(uid: String, onBack: () -> Unit, onOpenTransaction: (String) -> Unit) {
    val vm = appViewModel(key = "trend-$uid") { CategoryTrendViewModel(it.db, it.forecasts, uid) }
    val state by vm.state.collectAsStateWithLifecycle()
    val category = state?.first
    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text(category?.title ?: "Category") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        val detail = state?.second ?: return@Scaffold
        val color = themedColor(category?.color)
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).testTag("category-trend"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val over = detail.limit != null && detail.runRate > detail.limit
                AppCard(
                    Modifier.weight(1f).testTag("trend-projected"),
                    colors = CardDefaults.cardColors(containerColor = if (over) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Projected this month", style = MaterialTheme.typography.labelMedium)
                        Text(money(detail.runRate), style = MaterialTheme.typography.titleLarge)
                        detail.limit?.let { Text("limit ${money(it)}", style = MaterialTheme.typography.bodySmall) }
                    }
                }
                AppCard(Modifier.weight(1f).testTag("trend-average")) {
                    Column(Modifier.padding(14.dp)) {
                        Text("3-month average", style = MaterialTheme.typography.labelMedium)
                        Text(detail.average?.let(::money) ?: "–", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Monthly spending", style = MaterialTheme.typography.titleMedium)
                    TrendLine(detail, color)
                    Text(
                        "Solid: spent. Dashed segment: this month's pace.${if (detail.limit != null) " Red line: limit." else ""} Grey dotted line: 3-month average.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AppCard(Modifier.fillMaxWidth().testTag("trend-weekdays")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("When it happens", style = MaterialTheme.typography.titleMedium)
                    Text(
                        detail.fridayToSundayPercent?.let { "Friday to Sunday make up $it% of the last 3 months." } ?: "No spending in the last 3 full months.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (detail.fridayToSundayPercent != null) WeekdayBars(detail.weekdays, color)
                }
            }
            if (detail.largest.isNotEmpty()) {
                Text("Largest this month", style = MaterialTheme.typography.titleMedium)
                detail.largest.forEach { op ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("trend-op-${op.uid}"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(op.comment?.takeIf { it.isNotBlank() } ?: category?.title.orEmpty(), maxLines = 1)
                            Text(op.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("-${money(op.amount)}")
                    }
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TrendLine(detail: CategoryDetail, color: Color) {
    val limitColor = Semantic.expense
    val averageColor = MaterialTheme.colorScheme.outline
    val values = detail.months.map { it.second }
    val summary = detail.months.joinToString("; ") { "${it.first}: ${money(it.second)}" }
    Canvas(Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = "Monthly spending. $summary" }) {
        val top = maxOf(values.max(), detail.limit ?: 0, detail.runRate, detail.average ?: 0, 1L).toFloat() * 1.1f
        val pad = 6.dp.toPx()
        val step = size.width / (values.size - 1).coerceAtLeast(1)
        fun point(i: Int, v: Long) = Offset(step * i, size.height - pad - (size.height - 2 * pad) * v / top)
        fun hline(v: Long, c: Color, effect: PathEffect) =
            drawLine(c, Offset(0f, point(0, v).y), Offset(size.width, point(0, v).y), strokeWidth = 1.5.dp.toPx(), pathEffect = effect)
        detail.average?.let { hline(it, averageColor, PathEffect.dashPathEffect(floatArrayOf(3f, 8f))) }
        detail.limit?.let { hline(it, limitColor, PathEffect.dashPathEffect(floatArrayOf(14f, 8f))) }
        val solid = Path()
        for (i in 0 until values.lastIndex) { // the current month is drawn as the dashed run-rate segment instead
            val p = point(i, values[i])
            if (i == 0) solid.moveTo(p.x, p.y) else solid.lineTo(p.x, p.y)
        }
        drawPath(solid, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        val last = values.lastIndex
        if (last >= 1) {
            drawLine(color, point(last - 1, values[last - 1]), point(last, detail.runRate), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
            drawCircle(color, 5.dp.toPx(), point(last, detail.runRate), style = Stroke(2.5.dp.toPx()))
        }
    }
    Row(Modifier.fillMaxWidth()) {
        detail.months.forEachIndexed { i, (month, _) ->
            val show = i == 0 || i == detail.months.lastIndex || i % 3 == 0
            Text(
                if (show) month.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) else "", Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeekdayBars(weekdays: List<Long>, color: Color) {
    val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val faint = color.copy(alpha = 0.4f)
    Canvas(Modifier.fillMaxWidth().height(80.dp).semantics { contentDescription = "Spending by weekday. " + names.zip(weekdays).joinToString("; ") { "${it.first}: ${money(it.second)}" } }) {
        val top = weekdays.max().coerceAtLeast(1L).toFloat()
        val slot = size.width / 7
        val width = slot * 0.6f
        weekdays.forEachIndexed { i, v ->
            val h = size.height * v / top
            drawRoundRect(if (i >= 4) color else faint, Offset(slot * i + (slot - width) / 2, size.height - h), Size(width, h), CornerRadius(6f))
        }
    }
    Row(Modifier.fillMaxWidth()) {
        names.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
