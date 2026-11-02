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

    private fun transfer(uid: String, from: String, to: String, sent: Long, received: Long? = null, receivedCurrency: String? = null) = HistoryRow(
        uid, true, "2026-10-25", uid, "$from → $to", "", "", null, null, null, "",
        transfer = TransferSides(from, to, from, to, sent, "KZT", received, receivedCurrency),
    )

    @Test
    fun transfersBetweenTheSameAccounts_areAddedUp_onBothSides() {
        val groups = groupTransfers(
            listOf(
                transfer("a", "Main", "Deposit", 100_000), transfer("b", "Main", "Deposit", 250_000),
                transfer("c", "Main", "USD", 450_000, received = 1_000, receivedCurrency = "USD"),
                transfer("d", "Main", "USD", 225_000, received = 500, receivedCurrency = "USD"),
                transfer("e", "Deposit", "Main", 7_000), // the reverse direction is its own flow
                row("x", "cafe", false, 5_000), // not a transfer
            )
        )
        assertEquals(listOf("Main-Deposit", "Main-USD", "Deposit-Main"), groups.map { it.key })
        assertEquals("₸3,500", groups[0].amount) // 1,000 + 2,500 sent; same currency: no received side
        assertEquals("₸6,750 → $15", groups[1].amount) // 4,500 + 2,250 sent, $10 + $5 received
        assertEquals(2, groups[1].rows.size)
        assertEquals("Main → Deposit", groups[0].title)
    }

    @Test
    fun chartSlices_areSharesOfTheirOwnDirection_withTheCategoryColors_andSkipTransfers() {
        val groups = groupByCategory(
            listOf(
                row("a", "cafe", false, 1_000), row("b", "taxi", false, 3_000), row("c", "salary", true, 50_000),
                row("t", null, false, 9_999, transfer = true),
            )
        )
        val expenses = chartSlices(groups, income = false)
        assertEquals(listOf("taxi", "cafe"), expenses.map { it.title })
        assertEquals(listOf(75, 25), expenses.map { it.percent })
        assertEquals(1f, expenses.sumOf { it.fraction.toDouble() }.toFloat(), 0.0001f)
        assertEquals(listOf(100), chartSlices(groups, income = true).map { it.percent })
        assertEquals(emptyList<ChartSlice>(), chartSlices(emptyList(), income = false))
    }
}
