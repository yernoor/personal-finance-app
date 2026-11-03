package dev.yernur.moneytracker.data.sheets

import dev.yernur.moneytracker.data.DEFAULT_CURRENCY
import dev.yernur.moneytracker.data.db.AccountEntity
import dev.yernur.moneytracker.data.db.CategoryEntity
import dev.yernur.moneytracker.data.db.TagEntity
import dev.yernur.moneytracker.data.db.TransactionEntity
import dev.yernur.moneytracker.data.db.TransactionTagEntity
import dev.yernur.moneytracker.data.db.TransactionType
import dev.yernur.moneytracker.data.db.TransferEntity
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

/** What the phone holds before the sheet is read; rows are matched to it by UID. */
class LocalSnapshot(
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val tags: List<TagEntity>,
    val transactions: List<TransactionEntity>,
    val tagLinks: List<TransactionTagEntity>,
    val transfers: List<TransferEntity>,
)

/** Rows to write (new or changed) and tombstones to apply. Rows of the sheet that equal the phone's are not listed. */
class SheetChanges(
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val tags: List<TagEntity>,
    val transactions: List<TransactionEntity>,
    /** Tag uids of every changed transaction (replaces its links). */
    val transactionTags: Map<String, Set<String>>,
    val transfers: List<TransferEntity>,
    val deletedTransactions: List<String>,
    val deletedTransfers: List<String>,
    val added: Int,
    val updated: Int,
    val warnings: List<String>,
)

/**
 * The reverse of [SheetRows]: turns the tabs back into entities and compares them with the phone. The sheet wins for the rows it
 * has; rows only the phone has are left alone. Only the columns the sheet shows are read, everything else (icons of accounts,
 * real-currency amounts, ...) is kept from the phone's row. Pure, so it is tested without a network.
 */
class SheetParser(
    private val local: LocalSnapshot,
    private val now: String,
    /** UTC time of the last sync: a phone row modified after it is newer than the sheet's copy and is kept (null = the sheet always wins). */
    private val syncedAt: String? = null,
    private val newUid: () -> String = { UUID.randomUUID().toString() },
) {
    private fun phoneNewer(modified: String) = syncedAt != null && modified > syncedAt

    private val warnings = mutableListOf<String>()
    private var added = 0
    private var updated = 0

    fun parse(tabs: Map<String, List<List<Any?>>>): SheetChanges {
        fun rows(tab: TabSpec) = tabs[tab.title].orEmpty().drop(1).filter { r -> r.any { it != null && it.toString().isNotBlank() } }

        val accounts = settleAll(rows(SheetLayout.ACCOUNTS), local.accounts.associateBy { it.uid }, { it.uid }, ::account, { it.modified }) { it.copy(modified = now) }
        val categories = settleAll(rows(SheetLayout.CATEGORIES), local.categories.associateBy { it.uid }, { it.uid }, ::category, { it.modified }) { it.copy(modified = now) }
        val tags = settleAll(rows(SheetLayout.TAGS), local.tags.associateBy { it.uid }, { it.uid }, ::tag, { it.modified }) { it.copy(modified = now) }

        val accountUidByName = (local.accounts.filter { !it.isRemoved } + accounts.all).associate { it.title.ifBlank { MAIN } to it.uid }
        val currencyOf = (local.accounts + accounts.all).associate { it.uid to it.currencyCode }
        // Live categories first, then removed ones, so old transactions of a deleted category keep pointing at it.
        val byUid = (local.categories.filter { !it.isRemoved } + categories.all).associateBy { it.uid }
        val liveCategories = byUid.values + local.categories.filter { it.isRemoved && it.uid !in byUid }
        val tagUidByName = (local.tags.filter { !it.isRemoved } + tags.all).associate { it.name to it.uid }.toMutableMap()
        val newTags = tags.changed.toMutableList()

        val oldTagsOf = local.tagLinks.groupBy({ it.transactionUid }, { it.tagUid }).mapValues { it.value.toSet() }
        val oldTransactions = local.transactions.associateBy { it.uid }
        val changedTags = mutableMapOf<String, Set<String>>()
        val liveTransactionUids = mutableSetOf<String>()
        val transactions = mutableListOf<TransactionEntity>()
        for (row in rows(SheetLayout.TRANSACTIONS)) {
            val parsed = transaction(row, oldTransactions, accountUidByName, liveCategories, currencyOf) ?: continue
            val tagUids = row.text(4)?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty().map { name ->
                tagUidByName.getOrPut(name) { TagEntity(newUid(), name, 0, null, now, now).also { newTags += it; added++ }.uid }
            }.toSet()
            liveTransactionUids += parsed.uid
            val old = oldTransactions[parsed.uid]
            if (old != null && phoneNewer(old.modified)) continue
            val tagsChanged = tagUids != oldTagsOf[parsed.uid].orEmpty()
            if (old != null && old == parsed && !tagsChanged) continue
            transactions += if (old == null) parsed.also { added++ } else parsed.copy(modified = now).also { updated++ }
            changedTags[parsed.uid] = tagUids
        }

        val oldTransfers = local.transfers.associateBy { it.uid }
        val liveTransferUids = mutableSetOf<String>()
        val transfers = mutableListOf<TransferEntity>()
        for (row in rows(SheetLayout.TRANSFERS)) {
            val parsed = transfer(row, oldTransfers, accountUidByName, currencyOf) ?: continue
            liveTransferUids += parsed.uid
            val old = oldTransfers[parsed.uid]
            if (old == parsed || (old != null && phoneNewer(old.modified))) continue
            transfers += if (old == null) parsed.also { added++ } else parsed.copy(modified = now).also { updated++ }
        }

        val deleted = rows(SheetLayout.DELETED)
        return SheetChanges(
            accounts.changed, categories.changed, newTags, transactions, changedTags, transfers,
            deleted.uidsOf("Transaction", liveTransactionUids).filter { oldTransactions[it]?.let { t -> !t.isRemoved && !phoneNewer(t.modified) } == true },
            deleted.uidsOf("Transfer", liveTransferUids).filter { oldTransfers[it]?.let { t -> !t.isRemoved && !phoneNewer(t.modified) } == true },
            added, updated, warnings,
        )
    }

    private class Settled<T>(val all: List<T>, val changed: List<T>)

    /** Reads [rows] with [read] (given the phone's rows by uid); keeps those that are new or differ, the changed ones restamped by [stamp]. */
    private fun <T : Any> settleAll(
        rows: List<List<Any?>>, old: Map<String, T>, uidOf: (T) -> String, read: (List<Any?>, Map<String, T>) -> T?, modifiedOf: (T) -> String, stamp: (T) -> T,
    ): Settled<T> {
        val all = mutableListOf<T>()
        val changed = mutableListOf<T>()
        for (row in rows) {
            val next = read(row, old) ?: continue
            val existing = old[uidOf(next)]
            all += next
            when {
                existing != null && phoneNewer(modifiedOf(existing)) -> Unit
                existing == null -> { added++; changed += next }
                existing != next -> { updated++; changed += stamp(next) }
            }
        }
        return Settled(all, changed)
    }

    private fun List<List<Any?>>.uidsOf(entity: String, live: Set<String>) =
        filter { it.text(0) == entity }.mapNotNull { it.text(1) }.filter { it !in live }

    // Accounts: Account, Currency, Active, Cached Balance, UID
    private fun account(r: List<Any?>, old: Map<String, AccountEntity>): AccountEntity? {
        val title = r.text(0) ?: return null.also { warnings += "account row without a name skipped" }
        val uid = r.text(4) ?: newUid()
        val existing = old[uid]
        val currency = r.text(1) ?: existing?.currencyCode ?: DEFAULT_CURRENCY
        val active = r.bool(2) ?: existing?.isActive ?: true
        return if (existing == null) {
            AccountEntity(uid, title, currency, null, null, null, null, false, active, false, false, now, now)
        } else {
            existing.copy(title = if (title == MAIN && existing.title.isBlank()) "" else title, currencyCode = currency, isActive = active, isRemoved = false)
        }
    }

    // Categories: Category, Type, Used, UID, Icon, Color, Limit, Position
    private fun category(r: List<Any?>, old: Map<String, CategoryEntity>): CategoryEntity? {
        val title = r.text(0) ?: return null.also { warnings += "category row without a name skipped" }
        val uid = r.text(3) ?: newUid()
        val existing = old[uid]
        val type = enumOrNull<TransactionType>(r.text(1)) ?: existing?.type ?: TransactionType.Expense
        val typed = r.text(5)?.let(::parseColor)
        // The sheet shows RGB only; an unchanged colour keeps the phone's value (with its alpha byte).
        val color = if (typed != null && (existing?.color == null || (existing.color and 0xFFFFFF) != (typed and 0xFFFFFF))) typed else existing?.color
        val limit = r.money(6)
        return if (existing == null) {
            CategoryEntity(uid, title, type, r.text(4), color, r.int(2) ?: 0, null, limit, r.int(7), false, now, now)
        } else {
            existing.copy(title = title, type = type, icon = r.text(4) ?: existing.icon, color = color, limitAmount = limit, position = r.int(7) ?: existing.position, isRemoved = false)
        }
    }

    // Tags: Tag, UID
    private fun tag(r: List<Any?>, old: Map<String, TagEntity>): TagEntity? {
        val name = r.text(0) ?: return null
        val uid = r.text(1) ?: newUid()
        return old[uid]?.copy(name = name, isRemoved = false) ?: TagEntity(uid, name, 0, null, now, now)
    }

    // Transactions: Date, Type, Account, Category, Tags, Amount, Currency, Net (KZT), Comment, Created, Modified, UID
    private fun transaction(
        r: List<Any?>, old: Map<String, TransactionEntity>, accountUidByName: Map<String, String>,
        categories: Collection<CategoryEntity>, currencyOf: Map<String, String>,
    ): TransactionEntity? {
        val uid = r.text(11) ?: newUid()
        val existing = old[uid]
        val date = r.date(0)
        val type = enumOrNull<TransactionType>(r.text(1))
        val accountUid = r.text(2)?.let(accountUidByName::get)
        val amount = r.money(5)?.let { kotlin.math.abs(it) }
        if (date == null || type == null || accountUid == null || amount == null) {
            warnings += "transaction row skipped (date, type, account or amount unreadable): ${r.text(11) ?: "row without UID"}"
            return null
        }
        val categoryName = r.text(3)
        val categoryUid = categoryName?.let { n -> (categories.firstOrNull { it.title == n && it.type == type } ?: categories.firstOrNull { it.title == n })?.uid }
        if (categoryName != null && categoryUid == null) warnings += "unknown category \"$categoryName\" left empty on transaction $uid"

        val currency = r.text(6) ?: currencyOf[accountUid]
        val defaultAmount = r.money(7)?.let { kotlin.math.abs(it) } ?: existing?.amountInDefaultCurrency ?: amount.takeIf { currency == DEFAULT_CURRENCY }
        if (existing == null) {
            return TransactionEntity(uid, type, accountUid, categoryUid, defaultAmount, null, null, amount, currency, date, r.text(8), r.text(9) ?: now, r.text(10) ?: now)
        }
        // A changed amount makes the stored real-currency amount stale, so it goes with it.
        val stale = existing.amountInAccountCurrency != amount
        return existing.copy(
            type = type, accountUid = accountUid, categoryUid = categoryUid, amountInDefaultCurrency = defaultAmount,
            amountInRealCurrency = if (stale) null else existing.amountInRealCurrency,
            realCurrencyCode = if (stale) null else existing.realCurrencyCode,
            amountInAccountCurrency = amount, accountCurrencyCode = currency, date = date, comment = r.text(8), isRemoved = false,
        )
    }

    // Transfers: Date, From Account, To Account, From Amount, From Cur, To Amount, To Cur, Comment, UID, Created, Modified
    private fun transfer(r: List<Any?>, old: Map<String, TransferEntity>, accountUidByName: Map<String, String>, currencyOf: Map<String, String>): TransferEntity? {
        val uid = r.text(8) ?: newUid()
        val existing = old[uid]
        val date = r.date(0)
        val from = r.text(1)?.let(accountUidByName::get)
        val to = r.text(2)?.let(accountUidByName::get)
        val fromAmount = r.money(3)
        if (date == null || from == null || to == null || fromAmount == null) {
            warnings += "transfer row skipped (date, account or amount unreadable): ${r.text(8) ?: "row without UID"}"
            return null
        }
        val fromCurrency = r.text(4) ?: currencyOf[from].orEmpty()
        val toCurrency = r.text(6) ?: currencyOf[to]
        if (existing == null) {
            return TransferEntity(uid, from, to, fromAmount, fromCurrency, r.money(5), toCurrency, date, r.text(7), r.text(9) ?: now, r.text(10) ?: now)
        }
        return existing.copy(
            fromAccountUid = from, toAccountUid = to, fromAmount = fromAmount, fromCurrencyCode = fromCurrency, toAmount = r.money(5),
            toCurrencyCode = toCurrency, date = date, comment = r.text(7), isRemoved = false,
        )
    }

    companion object {
        const val MAIN = "Main"
        private val EPOCH = LocalDate.of(1899, 12, 30)

        /** `#RRGGBB` to an opaque ARGB int; null when it is not a colour. */
        fun parseColor(text: String): Int? = text.removePrefix("#").takeIf { it.length == 6 }?.toIntOrNull(16)?.let { 0xFF000000.toInt() or it }

        private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? = enumValues<E>().firstOrNull { it.name.equals(name, ignoreCase = true) }

        fun List<Any?>.text(i: Int): String? = getOrNull(i)?.let { if (it is Number) decimal(it)?.stripTrailingZeros()?.toPlainString() else it.toString().trim() }?.takeIf { it.isNotEmpty() }
        fun List<Any?>.int(i: Int): Int? = decimal(getOrNull(i))?.toInt()
        fun List<Any?>.bool(i: Int): Boolean? = when (val v = getOrNull(i)) {
            is Boolean -> v
            is String -> v.trim().uppercase().let { if (it == "TRUE") true else if (it == "FALSE") false else null }
            else -> null
        }

        /** Units (`1234.5`) to minor units (123450). Tolerates a typed `1 234,50`. */
        fun List<Any?>.money(i: Int): Long? = decimal(getOrNull(i))?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP)?.toLong()

        /** A serial number, or typed text `yyyy-mm-dd`, to the ISO date. */
        fun List<Any?>.date(i: Int): String? {
            val v = getOrNull(i) ?: return null
            if (v is Number) return EPOCH.plusDays(v.toLong()).toString()
            return runCatching { LocalDate.parse(v.toString().trim()).toString() }.getOrNull()
        }

        private fun decimal(v: Any?): BigDecimal? = when (v) {
            null -> null
            is Number -> v.toString().toBigDecimalOrNull()
            else -> v.toString().replace(" ", "").replace(",", ".").toBigDecimalOrNull()
        }
    }
}
