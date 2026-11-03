package dev.yernur.moneytracker.data

import dev.yernur.moneytracker.data.db.TransactionType
import dev.yernur.moneytracker.data.db.TxDetail
import dev.yernur.moneytracker.data.db.TxLite
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CategoryOperationsTest {
    private fun tx(uid: String, date: String, type: TransactionType, category: String?, amount: Long) =
        TxDetail(uid, date, type, category, "acc", null, amount)

    private val details = listOf(
        tx("1", "2026-10-30", TransactionType.Expense, "cafe", 1900),
        tx("2", "2026-10-12", TransactionType.Expense, "cafe", 250_000),
        tx("3", "2026-11-01", TransactionType.Expense, "cafe", 7_000), // next month
        tx("4", "2026-10-12", TransactionType.Income, "cafe", 5_000), // other type
        tx("5", "2026-10-15", TransactionType.Expense, null, 300), // balance correction
    )
    private val october = DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))

    @Test
    fun listedOperations_addUpToTheCategoryRowOfTheBar() {
        val rows = details.map { TxLite(it.date, it.type, it.categoryUid, it.amount) }
        val bar = buckets(rows, listOf(october)).single()
        val ops = categoryOperations(details, october, TransactionType.Expense, "cafe")
        assertEquals(listOf("1", "2"), ops.map { it.uid })
        assertEquals(bar.expenseByCategory.getValue("cafe"), ops.sumOf { it.amount })
    }

    @Test
    fun noCategory_listsTheBalanceCorrections() {
        assertEquals(listOf("5"), categoryOperations(details, october, TransactionType.Expense, null).map { it.uid })
    }
}
