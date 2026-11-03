package dev.yernur.moneytracker.data.importer

import dev.yernur.moneytracker.data.db.AccountEntity
import dev.yernur.moneytracker.data.db.CategoryEntity
import dev.yernur.moneytracker.data.db.RateEntity
import dev.yernur.moneytracker.data.db.ReminderEntity
import dev.yernur.moneytracker.data.db.ReminderKind
import dev.yernur.moneytracker.data.db.TagEntity
import dev.yernur.moneytracker.data.db.TransactionEntity
import dev.yernur.moneytracker.data.db.TransactionTagEntity
import dev.yernur.moneytracker.data.db.TransactionType
import dev.yernur.moneytracker.data.db.TransferEntity

class ImportResult(
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val tags: List<TagEntity>,
    val transactions: List<TransactionEntity>,
    val transactionTags: List<TransactionTagEntity>,
    val transfers: List<TransferEntity>,
    val reminders: List<ReminderEntity>,
    val rates: List<RateEntity>,
    /** Things the importer had to skip or patch; empty for a clean file. */
    val warnings: List<String>,
    /** Rows whose account-currency amount came from the real-currency field. */
    val accountAmountFromReal: Int,
    /** Transactions with no usable account-currency amount at all (balances will be off). */
    val unresolvedAccountAmount: Int,
)

/**
 * Pure translation of the backup's tables into this app's entities. Only live rows (isRemoved = 0)
 * and live sync_link edges are imported; edges become real foreign keys.
 */
object MmbackupMapper {
    fun map(tables: BackupTables): ImportResult {
        val warnings = mutableListOf<String>()
        val edges = liveEdges(tables)

        val accounts = tables.live("account").map(::account)
        val accountsByUid = accounts.associateBy { it.uid }
        val categories = tables.live("category").map(::category)
        val categoryUids = categories.map { it.uid }.toSet()
        val tags = tables.live("tag").map(::tag)
        val tagUids = tags.map { it.uid }.toSet()

        var fromReal = 0
        var unresolved = 0
        val transactions = mutableListOf<TransactionEntity>()
        val links = mutableListOf<TransactionTagEntity>()
        for (row in tables.live("transaction")) {
            val uid = row.str("uid") ?: continue
            val e = edges["Transaction" to uid].orEmpty()
            val account = e["Account"]?.firstOrNull()?.let(accountsByUid::get)
            val type = enumOrNull<TransactionType>(row.str("type"))
            if (account == null || type == null) {
                warnings += "transaction skipped (account or type unresolved): $uid"
                continue
            }
            val resolved = accountAmount(row, account)
            if (resolved.fromReal) fromReal++
            if (resolved.amount == null) unresolved++
            transactions += transaction(row, type, account.uid, e["Category"]?.firstOrNull()?.takeIf { it in categoryUids }, resolved)
            links += e["Tag"].orEmpty().filter { it in tagUids }.map { TransactionTagEntity(uid, it) }
        }

        val transfers = tables.live("transfer").mapNotNull { row ->
            val uid = row.str("uid") ?: return@mapNotNull null
            val e = edges["Transfer" to uid].orEmpty()
            val from = e["FromAccount"]?.firstOrNull()?.takeIf { it in accountsByUid }
            val to = e["ToAccount"]?.firstOrNull()?.takeIf { it in accountsByUid }
            val fromAmount = row.long("fromAmount")
            if (from == null || to == null || fromAmount == null) {
                warnings += "transfer skipped (account or amount unresolved): $uid"
                return@mapNotNull null
            }
            transfer(row, from, to, fromAmount)
        }

        return ImportResult(
            accounts, categories, tags, transactions, links, transfers,
            tables.live("reminding").mapNotNull { reminder(it, warnings) },
            tables.rows("rates").mapNotNull(::rate),
            warnings, fromReal, unresolved,
        )
    }

    private fun BackupTables.live(table: String) = rows(table).filter { !it.bool("isRemoved") }

    /** (entityType, entityUid) -> otherType -> [otherUid], live edges only. */
    private fun liveEdges(tables: BackupTables): Map<Pair<String, String>, Map<String, List<String>>> =
        tables.live("sync_link")
            .groupBy { (it.str("entityType") ?: "") to (it.str("entityUid") ?: "") }
            .mapValues { (_, rows) ->
                rows.groupBy({ it.str("otherType") ?: "" }, { it.str("otherUid") ?: "" })
            }

    private class AccountAmount(val amount: Long?, val fromReal: Boolean)

    /**
     * The backup leaves amountInAccountCurrency NULL when the entry was typed in the account's own currency
     * and keeps it in amountInRealCurrency instead (verified: closes every balance gap, docs/FORMAT.md 4b).
     */
    private fun accountAmount(row: Row, account: AccountEntity): AccountAmount {
        row.long("amountInAccountCurrency")?.let { return AccountAmount(it, false) }
        val real = row.long("amountInRealCurrency")
        if (real != null && row.str("realCurrencyCode") == account.currencyCode) return AccountAmount(real, true)
        return AccountAmount(null, false)
    }

    private fun account(r: Row) = AccountEntity(
        uid = r.str("uid")!!, title = r.str("title").orEmpty(), currencyCode = r.str("currencyCode").orEmpty(),
        icon = r.str("icon"), color = r.long("color")?.toInt(), position = r.int("position"), comment = r.str("comment"),
        ignoreInBalance = r.bool("ignoreInBalance"), isActive = r.bool("isActive"),
        isArchived = r.bool("isArchived"), isFavorite = r.bool("isFavorite"),
        created = r.str("created").orEmpty(), modified = r.str("modified").orEmpty(),
    )

    private fun category(r: Row) = CategoryEntity(
        uid = r.str("uid")!!, title = r.str("title").orEmpty(),
        type = enumOrNull<TransactionType>(r.str("type")) ?: TransactionType.Expense,
        icon = r.str("icon"), color = r.long("color")?.toInt(), useCount = r.int("useCount") ?: 0,
        lastUse = r.str("lastUse"), limitAmount = r.long("limitAmount"), position = r.int("position"),
        isArchived = r.bool("isArchived"), created = r.str("created").orEmpty(), modified = r.str("modified").orEmpty(),
    )

    private fun tag(r: Row) = TagEntity(
        uid = r.str("uid")!!, name = r.str("name").orEmpty(), useCount = r.int("useCount") ?: 0,
        lastUse = r.str("lastUse"), created = r.str("created").orEmpty(), modified = r.str("modified").orEmpty(),
    )

    private fun transaction(r: Row, type: TransactionType, accountUid: String, categoryUid: String?, amount: AccountAmount) =
        TransactionEntity(
            uid = r.str("uid")!!, type = type, accountUid = accountUid, categoryUid = categoryUid,
            amountInDefaultCurrency = r.long("amountInDefaultCurrency"),
            amountInRealCurrency = r.long("amountInRealCurrency"), realCurrencyCode = r.str("realCurrencyCode"),
            amountInAccountCurrency = amount.amount, accountCurrencyCode = r.str("accountCurrencyCode"),
            date = r.str("date").orEmpty(), comment = r.str("comment"),
            created = r.str("created").orEmpty(), modified = r.str("modified").orEmpty(),
        )

    private fun transfer(r: Row, from: String, to: String, fromAmount: Long) = TransferEntity(
        uid = r.str("uid")!!, fromAccountUid = from, toAccountUid = to, fromAmount = fromAmount,
        fromCurrencyCode = r.str("fromCurrencyCode").orEmpty(), toAmount = r.long("toAmount"),
        toCurrencyCode = r.str("toCurrencyCode"), date = r.str("date").orEmpty(), comment = r.str("comment"),
        created = r.str("created").orEmpty(), modified = r.str("modified").orEmpty(),
    )

    private fun reminder(r: Row, warnings: MutableList<String>): ReminderEntity? {
        val kind = when (r.str("remindingType")) {
            "reminding" -> ReminderKind.Reminding
            "regularPayments" -> ReminderKind.RegularPayments
            else -> {
                warnings += "reminder skipped (unknown remindingType): ${r.str("uid")}"
                return null
            }
        }
        return ReminderEntity(
            uid = r.str("uid")!!, kind = kind, title = r.str("title").orEmpty(), text = r.str("text"),
            period = r.str("period").orEmpty(), transactionType = enumOrNull<TransactionType>(r.str("transactionType")),
            startDate = r.str("startDate").orEmpty(), endDate = r.str("endDate"), time = padTime(r.str("time")),
            enabled = r.bool("enabled"), amount = r.long("amount"), addAutomatically = r.bool("addAutomatically"),
            customPeriodData = r.str("customPeriodData"),
            created = r.str("created").orEmpty(), modified = r.str("modified").orEmpty(),
        )
    }

    private fun rate(r: Row): RateEntity? {
        val value = r.double("value") ?: return null
        return RateEntity(r.str("fromCurrencyCode") ?: return null, r.str("toCurrencyCode") ?: return null, value, r.str("modified").orEmpty())
    }

    /** The backup stores "10:0" for 10:00. */
    fun padTime(time: String?): String {
        val parts = time.orEmpty().split(":")
        if (parts.size != 2) return time.orEmpty()
        return "%02d:%02d".format(parts[0].toIntOrNull() ?: 0, parts[1].toIntOrNull() ?: 0)
    }

    private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? = enumValues<E>().firstOrNull { it.name == name }
}
