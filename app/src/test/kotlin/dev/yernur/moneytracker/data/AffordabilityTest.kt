package dev.yernur.moneytracker.data

import dev.yernur.moneytracker.data.db.CategoryEntity
import dev.yernur.moneytracker.data.db.ProjectionMode
import dev.yernur.moneytracker.data.db.ReminderEntity
import dev.yernur.moneytracker.data.db.ReminderKind
import dev.yernur.moneytracker.data.db.TransactionType
import dev.yernur.moneytracker.data.db.TxLite
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AffordabilityTest {
    /** Day 10 of a 31-day month: 100,000 spent so far, so the run-rate is 10,000 a day. */
    private val today = LocalDate.of(2026, 10, 10)
    private val longAgo = LocalDate.of(2023, 9, 14)

    private fun category(limit: Long? = null) =
        CategoryEntity("a", "a", TransactionType.Expense, null, null, 0, null, limit, null, false, "", "", false, null, ProjectionMode.Include)

    private fun forecastOf(limit: Long? = null) = forecast(
        listOf(TxLite("2026-10-04", TransactionType.Expense, "a", 60_000), TxLite("2026-10-09", TransactionType.Expense, "a", 40_000)),
        listOf(category(limit)), today, longAgo, 100,
    )

    private fun reminder(type: TransactionType, start: String, amount: Long? = null, period: String = "Month", end: String? = null, enabled: Boolean = true) =
        ReminderEntity("r", ReminderKind.Reminding, "r", null, period, type, start, end, "10:00", enabled, amount, false, null, "", "")

    private val payday = reminder(TransactionType.Income, "2026-09-20") // next: 2026-10-20, ten days away
    private val rent = reminder(TransactionType.Expense, "2026-08-15", amount = 20_000) // 2026-10-15 falls inside

    private fun check(price: Long, balance: Long = 500_000, limit: Long? = null, saving: Long? = null, reminders: List<ReminderEntity> = listOf(payday, rent)) =
        affordability(price, "a", balance, forecastOf(limit), reminders, listOf("Vacation"), saving, today)

    @Test
    fun freeMoney_isBalanceMinusExpectedSpendingMinusScheduledPayments() {
        val r = check(50_000)
        assertEquals(LocalDate.of(2026, 10, 20), r.horizon)
        assertEquals(10, r.daysToHorizon)
        assertEquals(100_000L, r.expected) // 310,000 run-rate / 31 * 10
        assertEquals(20_000L, r.scheduled)
        assertEquals(1, r.scheduledCount)
        assertEquals(380_000L, r.free)
        assertEquals(330_000L, r.afterPurchase)
        assertEquals(Verdict.Yes, r.verdict)
    }

    @Test
    fun verdict_isTightWhenThePriceTakesMoreThanHalfOfTheFreeMoney_andNoWhenItDoesNotFit() {
        assertEquals(Verdict.Yes, check(190_000).verdict) // exactly 50% of 380,000
        assertEquals(Verdict.Tight, check(190_001).verdict)
        assertEquals(Verdict.Tight, check(380_000).verdict) // fits to the cent
        assertEquals(Verdict.No, check(380_001).verdict)
    }

    @Test
    fun verdict_isTightWhenTheCategoryLimitWouldBeExceeded() {
        val r = check(50_000, limit = 320_000) // run-rate 310,000 + 50,000 = 360,000
        assertEquals(40_000L, r.limit!!.over)
        assertEquals(Verdict.Tight, r.verdict)
        assertNull(check(50_000).limit) // no limit set: nothing to show
    }

    @Test
    fun noIncomeReminder_meansTheHorizonIsTheEndOfTheMonth() {
        val r = check(10_000, reminders = emptyList())
        assertEquals(LocalDate.of(2026, 10, 31), r.horizon)
        assertEquals(21, r.daysToHorizon)
        assertEquals(210_000L, r.expected)
    }

    @Test
    fun disabledAndNonMonthlyReminders_areHandledSafely() {
        assertEquals(0L, check(1, reminders = listOf(payday, rent.copy(enabled = false))).scheduled)
        assertEquals(0L, check(1, reminders = listOf(payday, rent.copy(period = "Custom"))).scheduled) // unknown period: ignored
        assertEquals(LocalDate.of(2026, 10, 31), check(1, reminders = listOf(payday.copy(enabled = false))).horizon)
    }

    @Test
    fun goalDelay_isPriceOverDailySaving_onlyWithPositiveSaving() {
        val delayed = check(50_000, saving = 300_000) // 10,000 a day -> 5 days
        assertEquals(listOf(GoalDelay("Vacation", 5)), delayed.goals)
        assertEquals(emptyList<GoalDelay>(), check(50_000, saving = 0).goals)
        assertEquals(emptyList<GoalDelay>(), check(50_000, saving = -1).goals)
        assertEquals(emptyList<GoalDelay>(), check(50_000, saving = null).goals)
    }

    @Test
    fun occurrences_clampToShortMonths_andStopAtTheEndDate() {
        val monthly31 = reminder(TransactionType.Expense, "2026-01-31")
        val days = occurrences(monthly31, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 4, 30))
        assertEquals(listOf(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30)), days)

        val ended = reminder(TransactionType.Expense, "2026-01-05", end = "2026-02-10")
        assertEquals(listOf(LocalDate.of(2026, 2, 5)), occurrences(ended, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 6, 1)))

        val daily = reminder(TransactionType.Expense, "2026-10-01", period = "Day")
        assertEquals(3, occurrences(daily, LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 13)).size)
    }

    @Test
    fun monthlySaving_isTheMeanNetOfThePreviousFullMonths() {
        val rows = listOf(
            TxLite("2026-07-05", TransactionType.Income, null, 500_000), TxLite("2026-07-06", TransactionType.Expense, "a", 200_000),
            TxLite("2026-08-05", TransactionType.Income, null, 500_000), TxLite("2026-08-06", TransactionType.Expense, "a", 350_000),
            TxLite("2026-09-05", TransactionType.Income, null, 500_000), TxLite("2026-09-06", TransactionType.Expense, "a", 500_000),
            TxLite("2026-10-02", TransactionType.Expense, "a", 999_999), // this month: ignored
        )
        assertEquals(150_000L, monthlySaving(rows, today, longAgo)) // (300,000 + 150,000 + 0) / 3
        assertNull(monthlySaving(rows, today, LocalDate.of(2026, 10, 1))) // no earlier month exists
    }
}
