package dev.yernur.moneytracker.data.importer

import dev.yernur.moneytracker.data.db.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MmbackupMapperTest {
    private val ts = "2026-10-25T00:00:00.000Z"

    private fun account(uid: String, currency: String, removed: Long = 0) = mapOf(
        "uid" to uid, "title" to uid, "currencyCode" to currency, "isRemoved" to removed,
        "ignoreInBalance" to 0L, "isActive" to 1L, "isArchived" to 0L, "isFavorite" to 0L, "created" to ts, "modified" to ts,
    )

    private fun category(uid: String, removed: Long = 0) = mapOf(
        "uid" to uid, "title" to uid, "type" to "Expense", "useCount" to 0L, "limitAmount" to 5_000_000L,
        "isArchived" to 0L, "isRemoved" to removed, "created" to ts, "modified" to ts,
    )

    private fun tag(uid: String) = mapOf("uid" to uid, "name" to uid, "useCount" to 0L, "isRemoved" to 0L, "created" to ts, "modified" to ts)

    private fun tx(uid: String, type: String = "Expense", account: Long? = null, real: Long? = null, realCur: String? = null, removed: Long = 0) = mapOf(
        "uid" to uid, "type" to type, "amountInDefaultCurrency" to 100L, "amountInRealCurrency" to real,
        "realCurrencyCode" to realCur, "amountInAccountCurrency" to account, "accountCurrencyCode" to null,
        "date" to "2026-10-25", "isRemoved" to removed, "created" to ts, "modified" to ts,
    )

    private fun transfer(uid: String, from: Long, to: Long?, toCur: String?, removed: Long = 0) = mapOf(
        "uid" to uid, "fromAmount" to from, "fromCurrencyCode" to "KZT", "toAmount" to to, "toCurrencyCode" to toCur,
        "date" to "2026-10-25", "isRemoved" to removed, "created" to ts, "modified" to ts,
    )

    private fun link(type: String, uid: String, other: String, otherUid: String, removed: Long = 0) = mapOf(
        "entityType" to type, "entityUid" to uid, "otherType" to other, "otherUid" to otherUid, "isRemoved" to removed,
    )

    private fun tables(vararg parts: Pair<String, List<Row>>) = BackupTables(35, mapOf(*parts))

    @Test
    fun removedRowsAndRemovedEdges_areExcluded() {
        val result = MmbackupMapper.map(
            tables(
                "account" to listOf(account("a", "KZT"), account("gone", "KZT", removed = 1)),
                "category" to listOf(category("c")),
                "transaction" to listOf(tx("t1", account = 100), tx("t2", account = 200, removed = 1)),
                "sync_link" to listOf(
                    link("Transaction", "t1", "Account", "a"),
                    link("Transaction", "t1", "Category", "c", removed = 1), // removed edge: no category
                ),
            )
        )
        assertEquals(listOf("a"), result.accounts.map { it.uid })
        assertEquals(listOf("t1"), result.transactions.map { it.uid })
        assertNull(result.transactions.single().categoryUid)
    }

    @Test
    fun emptyAccountAmount_usesRealAmount_onlyWhenRealCurrencyIsTheAccountCurrency() {
        val result = MmbackupMapper.map(
            tables(
                "account" to listOf(account("usd", "USD")),
                "transaction" to listOf(
                    tx("cents", real = 19, realCur = "USD"), // 0.19 USD, account amount NULL
                    tx("other", real = 500, realCur = "EUR"), // different currency: cannot be used
                    tx("plain", account = 1_353_700),
                ),
                "sync_link" to listOf("cents", "other", "plain").map { link("Transaction", it, "Account", "usd") },
            )
        )
        val byUid = result.transactions.associateBy { it.uid }
        assertEquals(19L, byUid.getValue("cents").amountInAccountCurrency)
        assertEquals(19L, byUid.getValue("cents").amountInRealCurrency) // raw field kept
        assertNull(byUid.getValue("other").amountInAccountCurrency)
        assertEquals(1_353_700L, byUid.getValue("plain").amountInAccountCurrency)
        assertEquals(1, result.accountAmountFromReal)
        assertEquals(1, result.unresolvedAccountAmount)
    }

    @Test
    fun crossCurrencyTransfer_keepsBothAmounts_andMissingToAmountStaysNull() {
        val result = MmbackupMapper.map(
            tables(
                "account" to listOf(account("kzt", "KZT"), account("usd", "USD")),
                "transfer" to listOf(transfer("x", 4_513_300, 10_000, "USD"), transfer("same", 5_000, null, null)),
                "sync_link" to listOf(
                    link("Transfer", "x", "FromAccount", "kzt"), link("Transfer", "x", "ToAccount", "usd"),
                    link("Transfer", "same", "FromAccount", "kzt"), link("Transfer", "same", "ToAccount", "kzt"),
                ),
            )
        )
        val x = result.transfers.first { it.uid == "x" }
        assertEquals(4_513_300L, x.fromAmount)
        assertEquals(10_000L, x.toAmount)
        assertEquals("USD", x.toCurrencyCode)
        assertNull(result.transfers.first { it.uid == "same" }.toAmount)
    }

    @Test
    fun tagLinks_categoryFallback_andUnresolvedAccounts() {
        val result = MmbackupMapper.map(
            tables(
                "account" to listOf(account("a", "KZT")),
                "category" to listOf(category("c")),
                "tag" to listOf(tag("t1"), tag("t2")),
                "transaction" to listOf(tx("ok", account = 1), tx("orphan", account = 1)),
                "sync_link" to listOf(
                    link("Transaction", "ok", "Account", "a"), link("Transaction", "ok", "Category", "c"),
                    link("Transaction", "ok", "Tag", "t1"), link("Transaction", "ok", "Tag", "t2"),
                    link("Transaction", "orphan", "Account", "missing"),
                ),
            )
        )
        assertEquals(listOf("ok"), result.transactions.map { it.uid })
        assertEquals("c", result.transactions.single().categoryUid)
        assertEquals(setOf("t1", "t2"), result.transactionTags.map { it.tagUid }.toSet())
        assertEquals(1, result.warnings.size)
    }

    @Test
    fun reminderTime_isPadded_andKindsAreMapped() {
        assertEquals("10:00", MmbackupMapper.padTime("10:0"))
        assertEquals("20:00", MmbackupMapper.padTime("20:00"))
        val reminders = listOf(
            mapOf("uid" to "r", "remindingType" to "regularPayments", "period" to "Month", "transactionType" to "Expense", "time" to "10:0", "enabled" to 1L, "isRemoved" to 0L),
            mapOf("uid" to "u", "remindingType" to "mystery", "isRemoved" to 0L),
        )
        val result = MmbackupMapper.map(tables("reminding" to reminders))
        assertEquals("10:00", result.reminders.single().time)
        assertEquals(TransactionType.Expense, result.reminders.single().transactionType)
        assertEquals(1, result.warnings.size)
    }
}
