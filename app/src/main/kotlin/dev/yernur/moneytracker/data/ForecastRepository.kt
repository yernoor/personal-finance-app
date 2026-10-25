package dev.yernur.moneytracker.data

import dev.yernur.moneytracker.data.db.AppDatabase
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class ForecastRepository(
    private val db: AppDatabase,
    private val settings: AppSettings,
    private val today: () -> LocalDate = LocalDate::now,
) {
    /** The earliest month any projection needs: [HISTORY_MONTHS] full months before this one. */
    private fun window(now: LocalDate) = YearMonth.from(now).minusMonths(HISTORY_MONTHS.toLong()).atDay(1).toString() to now.toString()

    fun observe(): Flow<Forecast> {
        val now = today()
        val (from, to) = window(now)
        return combine(
            db.transactionDao().observeLite(from, to), db.categoryDao().observeAll(),
            db.transactionDao().observeEarliestDate(), settings.warnThreshold,
        ) { rows, categories, earliest, threshold ->
            forecast(rows, categories.filter { !it.isRemoved }, now, earliest?.let(LocalDate::parse), threshold)
        }
    }

    suspend fun current(): Forecast = observe().first()
}
