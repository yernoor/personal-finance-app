package dev.yernur.moneytracker.data

import dev.yernur.moneytracker.data.db.CategoryEntity
import dev.yernur.moneytracker.data.db.ProjectionMode
import dev.yernur.moneytracker.data.db.TransactionType
import dev.yernur.moneytracker.data.db.TxLite
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastTest {
    /** 2026-10-10: day 10 of a 31-day month; the 3 previous full months are Jul, Aug, Sep. */
    private val today = LocalDate.of(2026, 10, 10)
    private val longAgo = LocalDate.of(2023, 9, 14)

    private fun category(uid: String, limit: Long? = null, threshold: Int? = null, mode: ProjectionMode = ProjectionMode.Auto) =
        CategoryEntity(uid, uid, TransactionType.Expense, null, null, 0, null, limit, null, false, "", "", false, threshold, mode)

    private fun expense(date: String, uid: String?, amount: Long) = TxLite(date, TransactionType.Expense, uid, amount)

    private fun run(rows: List<TxLite>, categories: List<CategoryEntity>, earliest: LocalDate? = longAgo, threshold: Int = 100) =
        forecast(rows, categories, today, earliest, threshold)

    @Test
    fun runRate_isLinear_andEqualsSpentOnTheLastDay() {
        val rows = listOf(expense("2026-10-03", "a", 60_000), expense("2026-10-10", "a", 40_000))
        assertEquals(310_000L, run(rows, listOf(category("a"))).categories.single().runRate) // 100,000 / 10 * 31

        val lastDay = forecast(rows, listOf(category("a")), LocalDate.of(2026, 10, 31), longAgo, 100)
        assertEquals(100_000L, lastDay.categories.single().runRate)
    }

    @Test
    fun average_isTheMeanOfThePreviousThreeFullMonths_notIncludingThisOne() {
        val rows = listOf(
            expense("2026-07-05", "a", 30_000), expense("2026-08-05", "a", 60_000), expense("2026-09-05", "a", 90_000),
            expense("2026-06-05", "a", 999_999), // older than three months: ignored
            expense("2026-10-05", "a", 500_000), // current month: ignored
        )
        assertEquals(60_000L, run(rows, listOf(category("a"))).categories.single().average)
    }

    @Test
    fun average_usesOnlyMonthsThatExist() {
        val rows = listOf(expense("2026-09-05", "a", 90_000), expense("2026-10-02", "a", 10_000))
        val oneMonth = run(rows, listOf(category("a")), earliest = LocalDate.of(2026, 9, 5))
        assertEquals(90_000L, oneMonth.categories.single().average) // divided by 1, not 3

        val none = run(rows, listOf(category("a")), earliest = LocalDate.of(2026, 10, 2))
        assertNull(none.categories.single().average)
        assertNull(none.averageIncluded)
    }

    @Test
    fun oneOffCategories_areLeftOutOfOverall_unlessOverridden() {
        fun months(uid: String, perMonth: Int) =
            listOf("2026-07-%02d", "2026-08-%02d", "2026-09-%02d").flatMap { fmt -> (1..perMonth).map { expense(fmt.format(it), uid, 10_000) } }
        val rows = months("rare", 1) + months("often", 2) + months("forced", 1) + months("hidden", 5) +
            listOf("rare", "often", "forced", "hidden").map { expense("2026-10-02", it, 5_000) }
        val categories = listOf(
            category("rare"), category("often"), category("forced", mode = ProjectionMode.Include), category("hidden", mode = ProjectionMode.Exclude),
        )
        val byUid = run(rows, categories).categories.associateBy { it.categoryUid }
        assertFalse(byUid.getValue("rare").inOverall) // 1 operation a month
        assertTrue(byUid.getValue("often").inOverall) // exactly 2 a month
        assertTrue(byUid.getValue("forced").inOverall)
        assertFalse(byUid.getValue("hidden").inOverall)
    }

    @Test
    fun overallFigures_sumOnlyTheIncludedCategories() {
        val rows = listOf("2026-09-01", "2026-09-02", "2026-10-01").map { expense(it, "often", 30_000) } +
            expense("2026-09-03", "rare", 900_000) + expense("2026-10-02", "rare", 100_000)
        val f = run(rows, listOf(category("often"), category("rare")), earliest = LocalDate.of(2026, 9, 1))
        assertEquals(30_000L, f.spentIncluded) // "often" only
        assertEquals(130_000L, f.spentAll)
        assertEquals(93_000L, f.runRateIncluded) // 30,000 / 10 * 31
        assertEquals(60_000L, f.averageIncluded) // September only: 2 x 30,000
    }

    @Test
    fun warnings_compareAgainstLimitTimesThreshold() {
        val limit = 100_000L
        // spent 40,000 by day 10 -> run-rate 124,000; average 80,000
        val rows = listOf(expense("2026-09-10", "a", 240_000), expense("2026-08-10", "a", 0), expense("2026-10-05", "a", 40_000))
        val category = category("a", limit = limit)
        val at100 = run(rows, listOf(category), earliest = LocalDate.of(2026, 9, 1)).categories.single()
        assertEquals(setOf(WarningKind.RunRate, WarningKind.Average), at100.warnings) // average = 240,000 / 1 = 240,000
        val strict = run(rows, listOf(category.copy(warnThresholdPercent = 30)), earliest = LocalDate.of(2026, 9, 1)).categories.single()
        assertEquals(WarningKind.entries.toSet(), strict.warnings) // 30% of limit = 30,000 < spent 40,000
        val relaxed = run(rows, listOf(category.copy(warnThresholdPercent = 300)), earliest = LocalDate.of(2026, 9, 1)).categories.single()
        assertTrue(relaxed.warnings.isEmpty())
    }

    @Test
    fun categoryWithLimit_isShownEvenWithoutSpending_andNoCategoryIsTracked() {
        val f = run(listOf(expense("2026-10-02", null, 7_000)), listOf(category("quiet", limit = 50_000)))
        assertEquals(setOf("quiet", null), f.categories.map { it.categoryUid }.toSet())
        assertEquals(0L, f.categories.first { it.categoryUid == "quiet" }.spent)
        assertTrue(f.warnings.isEmpty())
    }

    @Test
    fun warnedCategoriesComeFirst_evenWhenSmaller() {
        val rows = listOf(expense("2026-10-05", "big", 900_000), expense("2026-10-05", "small", 70_000))
        val f = run(rows, listOf(category("big"), category("small", limit = 50_000)))
        assertEquals(listOf("small", "big"), f.categories.map { it.categoryUid })
    }
}
