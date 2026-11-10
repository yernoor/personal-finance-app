package dev.yernur.moneytracker.data

import dev.yernur.moneytracker.data.db.TransactionType
import dev.yernur.moneytracker.data.db.TxDetail
import dev.yernur.moneytracker.data.db.TxLite
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrendsTest {
    /** Day 10 of October: the six months shown are May to October. */
    private val today = LocalDate.of(2026, 10, 10)
    private val longAgo = LocalDate.of(2023, 9, 14)

    private fun expense(date: String, uid: String, amount: Long) = TxLite(date, TransactionType.Expense, uid, amount)

    // "a" falls every month; "b" is steady at 600,000 until October, when it jumps.
    private val rows = listOf(
        expense("2026-05-05", "a", 100_000), expense("2026-06-05", "a", 90_000), expense("2026-07-05", "a", 80_000),
        expense("2026-08-05", "a", 70_000), expense("2026-09-05", "a", 60_000), expense("2026-10-05", "a", 10_000),
        expense("2026-07-06", "b", 600_000), expense("2026-08-06", "b", 600_000), expense("2026-09-06", "b", 600_000),
        expense("2026-10-06", "b", 400_000),
    )

    private fun forecastOn(day: LocalDate) = forecast(rows.filter { LocalDate.parse(it.date) <= day }, emptyList(), day, longAgo, 100)

    @Test
    fun bars_showSixMonths_withTheProjectionOnTheCurrentOne() {
        val result = analysis(rows, forecastOn(today), today)
        assertEquals(YearMonth.of(2026, 5), result.bars.first().month)
        assertEquals(YearMonth.of(2026, 10), result.bars.last().month)
        assertEquals(410_000L, result.bars.last().spent)
        assertEquals(1_271_000L, result.bars.last().projected) // a: 10,000 / 10 * 31 = 31,000; b: 400,000 / 10 * 31 = 1,240,000
        assertNull(result.bars.first().projected)
        assertEquals(440_000L, result.average) // (100 + 90 + 680 + 670 + 660) thousand / 5
        assertEquals(189, result.projectedChangePercent) // (1,271,000 - 440,000) / 440,000 = 188.9%
    }

    @Test
    fun average_skipsMonthsBeforeTheFirstSpending() {
        val onlyRecent = rows.filter { it.date >= "2026-08-01" }
        val result = analysis(onlyRecent, forecastOn(today), today)
        assertEquals(665_000L, result.average) // mean of Aug (670,000) and Sep (660,000); May to Jul are not in the data
    }

    @Test
    fun categoryDelta_isRunRateAgainstTheThreeMonthAverage() {
        val byUid = analysis(rows, forecastOn(today), today).categories.associateBy { it.categoryUid }
        assertEquals(-56, byUid.getValue("a").deltaPercent) // 31,000 against a 70,000 average
        assertEquals(107, byUid.getValue("b").deltaPercent) // 1,240,000 against 600,000
        assertEquals(listOf(100_000L, 90_000L, 80_000L, 70_000L, 60_000L, 10_000L), byUid.getValue("a").monthly)
    }

    @Test
    fun delta_waitsForTenDaysOfTheMonth() {
        val early = LocalDate.of(2026, 10, 6)
        val byUid = analysis(rows, forecastOn(early), early).categories.associateBy { it.categoryUid }
        assertNull(byUid.getValue("b").deltaPercent)
    }

    @Test
    fun delta_isNotShownForACategoryWithNothingSpentYetThisMonth() {
        val quiet = rows + listOf(expense("2026-07-05", "d", 50_000), expense("2026-08-05", "d", 50_000), expense("2026-09-05", "d", 50_000))
        val byUid = analysis(quiet, forecast(quiet, emptyList(), today, longAgo, 100), today).categories.associateBy { it.categoryUid }
        assertNull(byUid.getValue("d").deltaPercent) // would be -100%
        assertEquals(0L, byUid.getValue("d").monthly.last())
    }

    @Test
    fun insights_flagTheBigRise_andThreeDropsInARow() {
        val result = analysis(rows, forecastOn(today), today)
        assertEquals(listOf(Insight(InsightKind.Rise, "b", 107), Insight(InsightKind.Fall, "a", 0)), result.insights)
    }

    @Test
    fun aSmallRise_isNotCalledOut() {
        val small = listOf(
            expense("2026-07-05", "c", 10_000), expense("2026-08-05", "c", 10_000), expense("2026-09-05", "c", 10_000),
            expense("2026-10-05", "c", 10_000), // run-rate 31,000: +210%, but only 21,000 more
        )
        val f = forecast(small, emptyList(), today, longAgo, 100)
        assertEquals(emptyList<Insight>(), analysis(small, f, today).insights)
    }

    private fun detail(uid: String, date: String, amount: Long, category: String = "a") =
        TxDetail(uid, date, TransactionType.Expense, category, "main", null, amount)

    @Test
    fun categoryDetail_hasTwelveMonths_weekdayShare_andTheLargestOperations() {
        val details = listOf(
            detail("1", "2026-09-04", 100), // Friday
            detail("2", "2026-09-05", 200), // Saturday
            detail("3", "2026-09-07", 100), // Monday
            detail("4", "2026-10-02", 70_000), detail("5", "2026-10-08", 90_000), detail("6", "2026-10-03", 20_000), detail("7", "2026-10-04", 5_000),
            detail("8", "2026-10-04", 999_999, category = "other"),
        )
        val result = categoryDetail(rows, details, "a", forecastOn(today), today)
        assertEquals(12, result.months.size)
        assertEquals(YearMonth.of(2025, 11), result.months.first().first)
        assertEquals(10_000L, result.months.last().second)
        assertEquals(31_000L, result.runRate)
        assertEquals(70_000L, result.average)
        assertEquals(75, result.fridayToSundayPercent) // (100 + 200) of 400
        assertEquals(100L, result.weekdays[0]) // Monday
        assertEquals(listOf("5", "4", "6"), result.largest.map { it.uid }) // October only, this category only, biggest first
    }

    @Test
    fun categoryDetail_withoutHistory_hasNoWeekdayShare() {
        val result = categoryDetail(rows, emptyList(), "a", forecastOn(today), today)
        assertNull(result.fridayToSundayPercent)
        assertEquals(emptyList<TxDetail>(), result.largest)
    }
}
