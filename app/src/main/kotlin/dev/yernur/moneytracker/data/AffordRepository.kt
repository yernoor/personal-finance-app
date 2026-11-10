package dev.yernur.moneytracker.data

import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.wealth.FxRepository
import dev.yernur.moneytracker.data.wealth.WealthRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first

/** Gathers the inputs of [affordability] from the database; the rules live in that pure function. */
class AffordRepository(
    private val db: AppDatabase,
    private val fx: FxRepository,
    private val forecasts: ForecastRepository,
    private val wealth: WealthRepository,
    private val today: () -> LocalDate = LocalDate::now,
) {
    /** Null when the account is unknown or has no known rate to the default currency. */
    suspend fun check(price: Long, categoryUid: String?, accountUid: String): AffordResult? {
        val now = today()
        val account = db.accountDao().get(accountUid) ?: return null
        val own = db.accountDao().observeBalances().first().firstOrNull { it.uid == accountUid }?.balance ?: 0L
        val balance = Money.convert(own, account.currencyCode, DEFAULT_CURRENCY, fx.allRates.first()) ?: return null
        val from = YearMonth.from(now).minusMonths(HISTORY_MONTHS.toLong()).atDay(1).toString()
        val saving = monthlySaving(
            db.transactionDao().getLite(from, now.toString()), now, db.transactionDao().earliestDate()?.let(LocalDate::parse),
        )
        val openGoals = wealth.goalProgress(now).filter { !it.second.reached }.map { it.first.title }
        return affordability(price, categoryUid, balance, forecasts.current(), db.reminderDao().getLive(), openGoals, saving, now)
    }
}
