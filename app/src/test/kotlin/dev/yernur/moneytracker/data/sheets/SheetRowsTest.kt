package dev.yernur.moneytracker.data.sheets

import dev.yernur.moneytracker.data.db.AccountEntity
import dev.yernur.moneytracker.data.db.CategoryEntity
import dev.yernur.moneytracker.data.db.TagEntity
import dev.yernur.moneytracker.data.db.TransactionEntity
import dev.yernur.moneytracker.data.db.TransactionTagEntity
import dev.yernur.moneytracker.data.db.TransactionType
import dev.yernur.moneytracker.data.db.TransferEntity
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetRowsTest {
    private val ts = "2026-10-25T10:00:00.000Z"

    private fun account(uid: String, title: String, currency: String, removed: Boolean = false) =
        AccountEntity(uid, title, currency, null, null, 0, null, false, true, false, false, ts, ts, removed)

    private val accounts = listOf(account("main", "", "KZT"), account("usd", "Freedom USD", "USD"), account("gone", "Old", "KZT", removed = true))
    private val categories = listOf(
        CategoryEntity("cafe", "Кафе", TransactionType.Expense, "cafe", 0xFFF1CC06.toInt(), 25, null, 10_000_000, 2, false, ts, ts),
        CategoryEntity("sal", "Зарплата", TransactionType.Income, "cash", null, 3, null, null, 0, false, ts, ts),
    )
    private val tags = listOf(TagEntity("t1", "долг", 1, null, ts, ts), TagEntity("t2", "pinduoduo", 1, null, ts, ts), TagEntity("t3", "old", 0, null, ts, ts, true))

    private fun tx(uid: String, type: TransactionType, account: String, category: String?, accountAmount: Long?, defaultAmount: Long?, date: String, removed: Boolean = false) =
        TransactionEntity(uid, type, account, category, defaultAmount, null, null, accountAmount, null, date, "=SUM(A1) note", ts, ts, removed)

    private val transactions = listOf(
        tx("b", TransactionType.Expense, "usd", "cafe", 19, 8_576, "2026-10-25"),
        tx("a", TransactionType.Expense, "main", "cafe", 1_353_700, 1_353_700, "2026-10-24"),
        tx("c", TransactionType.Income, "main", "sal", 100_000_000, 100_000_000, "2026-10-25"),
        tx("d", TransactionType.Expense, "main", null, 500, 500, "2026-10-25", removed = true),
    )
    private val transfers = listOf(
        TransferEntity("x", "main", "usd", 4_513_300, "KZT", 10_000, "USD", "2026-10-25", null, ts, ts),
        TransferEntity("y", "main", "main", 5_000, "KZT", null, null, "2026-10-25", "same", ts, ts, true),
    )

    private val tabs = SheetRows.build(
        accounts, categories, tags, transactions, listOf(TransactionTagEntity("b", "t2"), TransactionTagEntity("b", "t1"), TransactionTagEntity("b", "t3")),
        transfers, mapOf("main" to 459_671L, "usd" to 1_356L), "device-1", "2026-10-26T00:00:00.000Z",
    ).associateBy { it.spec.title }

    @Test
    fun everyRowHasExactlyAsManyCellsAsItsHeader() {
        tabs.values.forEach { tab -> tab.rows.forEach { assertEquals("${tab.spec.title}: $it", tab.spec.headers.size, it.size) } }
    }

    @Test
    fun transactions_areDecimalNamedSortedAndSigned() {
        val rows = tabs.getValue("Transactions").rows
        assertEquals(listOf("a", "b", "c"), rows.map { it.last() }) // oldest first; the removed one is not here
        val usd = rows[1]
        assertEquals(46_320L, usd[0]) // 2026-10-25 as a sheet serial number
        assertEquals(listOf("Expense", "Freedom USD", "Кафе"), usd.subList(1, 4))
        assertEquals("pinduoduo, долг", usd[4]) // removed tag t3 left out, names sorted
        assertEquals(BigDecimal("0.19"), usd[5]) // exact cents, not 0.19000000000000003
        assertEquals("USD", usd[6])
        assertEquals(BigDecimal("-85.76"), usd[7]) // an expense is negative in Net
        assertEquals("=SUM(A1) note", usd[8]) // stays text: values are written RAW, so it never runs as a formula
        val main = rows[0]
        assertEquals("Main", main[2]) // the backup's untitled default account
        assertEquals(BigDecimal("13537.00"), main[5])
        assertEquals(BigDecimal("1000000.00"), rows[2][7]) // income is positive
    }

    @Test
    fun transfers_keepBothSides_andLeaveTheReceivedSideEmptyWithinOneCurrency() {
        val rows = tabs.getValue("Transfers").rows
        assertEquals(1, rows.size) // the removed one is not here
        assertEquals(listOf("Main", "Freedom USD", BigDecimal("45133.00"), "KZT", BigDecimal("100.00"), "USD"), rows[0].subList(1, 7))
    }

    @Test
    fun accountsCategoriesAndTags() {
        assertEquals(listOf("Main", "KZT", true, BigDecimal("4596.71"), "main"), tabs.getValue("Accounts").rows[0])
        assertEquals(2, tabs.getValue("Accounts").rows.size) // removed accounts are not listed
        val cafe = tabs.getValue("Categories").rows.first { it[3] == "cafe" }
        assertEquals(listOf("Кафе", "Expense", 25, "cafe", "cafe", "#F1CC06", BigDecimal("100000.00"), 2), cafe)
        assertNull(tabs.getValue("Categories").rows.first { it[3] == "sal" }[6]) // no limit
        assertEquals(listOf(listOf("pinduoduo", "t2"), listOf("долг", "t1")), tabs.getValue("Tags").rows)
    }

    @Test
    fun deletionsGoToTheHiddenTab_andMetaDescribesTheDevice() {
        assertEquals(listOf(listOf("Transaction", "d", ts), listOf("Transfer", "y", ts), listOf("Account", "gone", ts), listOf("Tag", "t3", ts)), tabs.getValue("_deleted").rows)
        assertEquals(
            listOf(listOf("schemaVersion", 1), listOf("deviceId", "device-1"), listOf("lastSyncAt:device-1", "2026-10-26T00:00:00.000Z")),
            tabs.getValue("_meta").rows,
        )
    }

    @Test
    fun dateSerials_matchKnownSpreadsheetNumbers() {
        assertEquals(25_569L, SheetRows.dateSerial("1970-01-01")) // the well-known value for the Unix epoch
        assertEquals(45_183L, SheetRows.dateSerial("2023-09-14")) // the first day in the owner's ledger
        assertTrue(SheetRows.dateSerial("2026-10-25") > SheetRows.dateSerial("2026-10-24"))
    }
}
