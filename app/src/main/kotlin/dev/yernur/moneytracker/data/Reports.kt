package dev.yernur.moneytracker.data

import dev.yernur.moneytracker.data.db.TransactionType
import dev.yernur.moneytracker.data.db.TxLite
import java.time.LocalDate

/** Totals of one chart bar, in default-currency minor units. Category key null = no category. */
data class Bucket(
    val range: DateRange,
    val income: Long,
    val expense: Long,
    val incomeByCategory: Map<String?, Long>,
    val expenseByCategory: Map<String?, Long>,
) {
    val net get() = income - expense
}

fun buckets(rows: List<TxLite>, ranges: List<DateRange>): List<Bucket> {
    val dated = rows.map { LocalDate.parse(it.date) to it }
    return ranges.map { range ->
        val inRange = dated.filter { it.first in range }.map { it.second }
        fun byCategory(type: TransactionType) =
            inRange.filter { it.type == type }.groupBy { it.categoryUid }.mapValues { (_, list) -> list.sumOf { it.amount } }
        val income = byCategory(TransactionType.Income)
        val expense = byCategory(TransactionType.Expense)
        Bucket(range, income.values.sum(), expense.values.sum(), income, expense)
    }
}
