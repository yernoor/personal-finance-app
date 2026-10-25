package dev.yernur.moneytracker.data.sheets

import dev.yernur.moneytracker.data.db.AccountEntity
import dev.yernur.moneytracker.data.db.CategoryEntity
import dev.yernur.moneytracker.data.db.TagEntity
import dev.yernur.moneytracker.data.db.TransactionEntity
import dev.yernur.moneytracker.data.db.TransactionTagEntity
import dev.yernur.moneytracker.data.db.TransactionType
import dev.yernur.moneytracker.data.db.TransferEntity
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A tab's data rows (the header row is added by the writer). */
class TabData(val spec: TabSpec, val rows: List<List<Any?>>)

/**
 * Turns the local database into the sheet's rows: decimals instead of minor units, names beside the UIDs, live rows only
 * in the main tabs (deletions go to `_deleted`). Pure, so it is tested without a network.
 */
object SheetRows {
    fun build(
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        tags: List<TagEntity>,
        transactions: List<TransactionEntity>,
        tagLinks: List<TransactionTagEntity>,
        transfers: List<TransferEntity>,
        balances: Map<String, Long>,
        deviceId: String,
        now: String,
    ): List<TabData> {
        val accountName = accounts.associate { it.uid to it.title.ifBlank { "Main" } }
        val categoryName = categories.associate { it.uid to it.title }
        val tagName = tags.filter { !it.isRemoved }.associate { it.uid to it.name }
        val tagsOf = tagLinks.groupBy({ it.transactionUid }, { it.tagUid }).mapValues { (_, uids) -> uids.mapNotNull(tagName::get).sorted().joinToString(", ") }
        val currencyOf = accounts.associate { it.uid to it.currencyCode }

        val transactionRows = transactions.filter { !it.isRemoved }.sortedWith(compareBy({ it.date }, { it.created })).map { t ->
            listOf(
                dateSerial(t.date), t.type.name, accountName[t.accountUid] ?: t.accountUid, t.categoryUid?.let(categoryName::get).orEmpty(),
                tagsOf[t.uid].orEmpty(), t.amountInAccountCurrency?.let(::units), t.accountCurrencyCode ?: currencyOf[t.accountUid].orEmpty(),
                t.amountInDefaultCurrency?.let { units(if (t.type == TransactionType.Expense) -it else it) },
                t.comment.orEmpty(), t.created, t.modified, t.uid,
            )
        }
        val transferRows = transfers.filter { !it.isRemoved }.sortedWith(compareBy({ it.date }, { it.created })).map { t ->
            listOf(
                dateSerial(t.date), accountName[t.fromAccountUid] ?: t.fromAccountUid, accountName[t.toAccountUid] ?: t.toAccountUid,
                units(t.fromAmount), t.fromCurrencyCode, t.toAmount?.let(::units), t.toCurrencyCode, t.comment.orEmpty(), t.uid, t.created, t.modified,
            )
        }
        val accountRows = accounts.filter { !it.isRemoved }.sortedBy { it.position ?: Int.MAX_VALUE }.map {
            listOf(accountName.getValue(it.uid), it.currencyCode, it.isActive, units(balances[it.uid] ?: 0L), it.uid)
        }
        val categoryRows = categories.filter { !it.isRemoved }.sortedBy { it.position ?: Int.MAX_VALUE }.map {
            listOf(it.title, it.type.name, it.useCount, it.uid, it.icon.orEmpty(), it.color?.let { c -> "#%06X".format(c and 0xFFFFFF) }.orEmpty(), it.limitAmount?.let(::units), it.position)
        }
        val tagRows = tags.filter { !it.isRemoved }.sortedBy { it.name }.map { listOf(it.name, it.uid) }
        val deletedRows =
            transactions.filter { it.isRemoved }.map { listOf("Transaction", it.uid, it.modified) } +
                transfers.filter { it.isRemoved }.map { listOf("Transfer", it.uid, it.modified) } +
                accounts.filter { it.isRemoved }.map { listOf("Account", it.uid, it.modified) } +
                categories.filter { it.isRemoved }.map { listOf("Category", it.uid, it.modified) } +
                tags.filter { it.isRemoved }.map { listOf("Tag", it.uid, it.modified) }
        val metaRows = listOf(listOf("schemaVersion", SheetLayout.SCHEMA_VERSION), listOf("deviceId", deviceId), listOf("lastSyncAt:$deviceId", now))

        return listOf(
            TabData(SheetLayout.TRANSACTIONS, transactionRows), TabData(SheetLayout.TRANSFERS, transferRows),
            TabData(SheetLayout.ACCOUNTS, accountRows), TabData(SheetLayout.CATEGORIES, categoryRows), TabData(SheetLayout.TAGS, tagRows),
            TabData(SheetLayout.DELETED, deletedRows), TabData(SheetLayout.META, metaRows),
        )
    }

    /** Sheets counts days from 1899-12-30; a number there is a date once the column has a date format. */
    fun dateSerial(isoDate: String): Long = ChronoUnit.DAYS.between(EPOCH, LocalDate.parse(isoDate))

    private val EPOCH = LocalDate.of(1899, 12, 30)

    private fun units(minor: Long): BigDecimal = BigDecimal.valueOf(minor, 2)
}
