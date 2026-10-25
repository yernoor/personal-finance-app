package dev.yernur.moneytracker.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryGroupingTest {
    private fun row(uid: String, category: String?, income: Boolean, amount: Long, transfer: Boolean = false) = HistoryRow(
        uid, transfer, "2026-10-25", uid, category ?: "none", "", "", null, null, null, "",
        categoryUid = category, isIncome = income, defaultAmount = amount,
    )

    @Test
    fun sumsPerCategoryAndDirection_biggestFirst_andSkipsTransfers() {
        val groups = groupByCategory(
            listOf(
                row("a", "cafe", false, 1_000), row("b", "cafe", false, 2_500), row("c", "taxi", false, 9_000),
                row("d", "salary", true, 100_000), row("t", null, false, 777, transfer = true),
            )
        )
        assertEquals(listOf("salary-true", "taxi-false", "cafe-false"), groups.map { it.key })
        assertEquals(3_500L, groups.last().total)
        assertEquals(listOf("a", "b"), groups.last().rows.map { it.uid })
    }

    @Test
    fun theSameCategoryWithBothDirections_staysSeparate() {
        val groups = groupByCategory(listOf(row("a", null, false, 500), row("b", null, true, 200)))
        assertEquals(2, groups.size)
    }
}
