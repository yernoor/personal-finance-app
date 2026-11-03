package dev.yernur.moneytracker.widget

import dev.yernur.moneytracker.data.ForecastRepository
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.TransactionType
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.Locale

/** One category against its monthly limit, in default-currency minor units. */
data class LimitBar(val title: String, val spent: Long, val limit: Long)

data class CategorySpend(val title: String, val spent: Long)

data class RecentOp(val uid: String, val title: String, val subtitle: String, val amount: Long, val isIncome: Boolean)

/**
 * Everything the widgets show. Spending, limits and operations only: account balances and totals are never here
 * (owner decision: they must not appear on the home screen).
 */
data class WidgetData(
    val month: LocalDate,
    val monthSpent: Long,
    val projected: Long,
    val todaySpent: Long,
    val todayCount: Int,
    val overLimit: Boolean,
    val limits: List<LimitBar>,
    val topCategories: List<CategorySpend>,
    val recent: List<RecentOp>,
)

/** Reads the widget numbers from the same forecast the Home screen uses, so the two never disagree. */
suspend fun loadWidgetData(db: AppDatabase, forecasts: ForecastRepository, today: LocalDate = LocalDate.now()): WidgetData {
    val forecast = forecasts.current()
    val titles = db.categoryDao().getAll().associate { it.uid to it.title }
    val accounts = db.accountDao().getAll().associate { it.uid to it.title }
    val todayRows = db.transactionDao().getLite(today.toString(), today.toString()).filter { it.type == TransactionType.Expense }
    val limits = forecast.categories.filter { it.limit != null && it.limit > 0 }
        .map { LimitBar(titles[it.categoryUid] ?: "Other", it.spent, it.limit!!) }
        .sortedByDescending { it.spent.toDouble() / it.limit }
    val top = forecast.categories.filter { it.spent > 0 }.sortedByDescending { it.spent }
        .map { CategorySpend(titles[it.categoryUid] ?: "Balance correction", it.spent) }
    val recent = db.transactionDao().observeDetail(today.minusDays(60).toString(), today.toString()).first().take(12).map {
        RecentOp(
            it.uid, titles[it.categoryUid] ?: "Balance correction", it.comment?.takeIf { c -> c.isNotBlank() } ?: accounts[it.accountUid].orEmpty(),
            it.amount, it.type == TransactionType.Income,
        )
    }
    return WidgetData(
        month = today, monthSpent = forecast.spentAll, projected = forecast.runRateIncluded,
        todaySpent = todayRows.sumOf { it.amount }, todayCount = todayRows.size,
        overLimit = forecast.warnings.isNotEmpty(), limits = limits, topCategories = top, recent = recent,
    )
}

/** Whole currency units with a space between thousands ("412 300"): the widgets are too small for decimals. */
fun wholeUnits(minor: Long): String = String.format(Locale.US, "%,d", minor / 100).replace(',', ' ')

/** Fictional numbers for the widget picker previews: no real spending, no balances. */
fun sampleWidgetData(today: LocalDate = LocalDate.now()) = WidgetData(
    month = today, monthSpent = 41_230_000, projected = 58_400_000, todaySpent = 1_250_000, todayCount = 3, overLimit = false,
    limits = listOf(LimitBar("Food", 5_400_000, 8_000_000), LimitBar("Transport", 2_100_000, 3_000_000), LimitBar("Leisure", 3_300_000, 3_000_000)),
    topCategories = listOf(
        CategorySpend("Food", 12_000_000), CategorySpend("Home", 9_500_000), CategorySpend("Transport", 6_000_000),
        CategorySpend("Leisure", 4_800_000), CategorySpend("Health", 2_900_000),
    ),
    recent = listOf(
        RecentOp("s1", "Food", "Cafe", 450_000, false), RecentOp("s2", "Transport", "Taxi", 320_000, false),
        RecentOp("s3", "Salary", "Main", 25_000_000, true), RecentOp("s4", "Home", "Rent", 15_000_000, false),
        RecentOp("s5", "Health", "Pharmacy", 180_000, false), RecentOp("s6", "Leisure", "Cinema", 250_000, false),
        RecentOp("s7", "Food", "Market", 890_000, false), RecentOp("s8", "Transport", "Metro", 40_000, false),
    ),
)
