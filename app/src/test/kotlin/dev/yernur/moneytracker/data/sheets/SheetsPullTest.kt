package dev.yernur.moneytracker.data.sheets

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.LedgerRepository
import dev.yernur.moneytracker.data.TransactionDraft
import dev.yernur.moneytracker.data.TransferDraft
import dev.yernur.moneytracker.data.db.AccountEntity
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.CategoryEntity
import dev.yernur.moneytracker.data.db.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SheetsPullTest {
    private lateinit var db: AppDatabase
    private val settings = AppSettings().apply { setSpreadsheet("sid", "https://docs.google.com/spreadsheets/d/sid/edit") }

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        db.accountDao().insertAll(
            listOf(
                AccountEntity("main", "Main", "KZT", "wallet", 0xFF112233.toInt(), 0, null, false, true, false, false, "", ""),
                AccountEntity("usd", "Dollars", "USD", null, null, 1, null, false, true, false, false, "", ""),
            )
        )
        db.categoryDao().insertAll(listOf(CategoryEntity("cafe", "Кафе", TransactionType.Expense, null, 0xFF445566.toInt(), 0, null, 5_000_000L, 0, false, "", "")))
        val ledger = LedgerRepository(db)
        repeat(3) { ledger.saveTransaction(TransactionDraft(null, TransactionType.Expense, "main", "cafe", 100_000L * (it + 1), "2026-10-2${it + 1}", "c$it", emptySet())) }
        ledger.saveTransfer(TransferDraft(null, "main", "usd", 450_000, 1_000, "2026-10-25", null))
    }

    @After
    fun tearDown() = db.close()

    /** The sheet exactly as a push writes it, as the API would read it back (header row first; trailing empty cells are dropped). */
    private suspend fun pushedTabs(): MutableMap<String, MutableList<MutableList<Any?>>> {
        val tabs = SheetRows.build(
            db.accountDao().getAll(), db.categoryDao().getAll(), db.tagDao().getAll(), db.transactionDao().getAll(),
            db.transactionDao().getAllTagLinks(), db.transferDao().getAll(),
            db.accountDao().observeBalances().first().associate { it.uid to it.balance }, "device", "2026-10-26T00:00:00.000Z",
        )
        return tabs.associate { t ->
            t.spec.title to (listOf(t.spec.headers) + t.rows).map { row -> row.map { it?.let { v -> if (v is java.math.BigDecimal) v.toDouble() else v } }.toMutableList() }.toMutableList()
        }.toMutableMap()
    }

    private fun pull(tabs: Map<String, List<List<Any?>>>): PullResult = runBlocking {
        val body = JSONObject().put(
            "valueRanges",
            JSONArray(SheetLayout.ALL.map { JSONObject().put("values", JSONArray((tabs[it.title].orEmpty()).map { r -> JSONArray(r) })) }),
        ).toString()
        val api = SheetsApi(FakeTransport { ok(body) }, { "token" }, sleep = {}, jitter = { 0 })
        SheetsPull(db, api, settings) { "2026-11-03T00:00:00.000Z" }.pull()
    }

    @Test
    fun theSheetAPushJustWrote_changesNothing() = runBlocking {
        val before = db.transactionDao().getAll()
        val result = pull(pushedTabs())
        assertEquals(0, result.added + result.updated + result.deleted)
        assertEquals(emptyList<String>(), result.warnings)
        assertEquals(before, db.transactionDao().getAll())
        assertEquals(0xFF445566.toInt(), db.categoryDao().get("cafe")!!.color) // the sheet shows RGB only
        assertEquals("wallet", db.accountDao().get("main")!!.icon)
    }

    @Test
    fun aSameCurrencyTransfer_roundTripsUnchanged() = runBlocking {
        db.accountDao().insertAll(listOf(AccountEntity("sav", "Savings", "KZT", null, null, 6, null, false, true, false, false, "", "")))
        LedgerRepository(db).saveTransfer(TransferDraft(null, "main", "sav", 10_000, null, "2026-10-26", null))
        val result = pull(pushedTabs())
        assertEquals(0, result.added + result.updated)
        assertEquals(null, db.transferDao().getAll().single { it.toAccountUid == "sav" }.toCurrencyCode)
    }

    @Test
    fun twoAccountsWithTheSameName_keepTheirOwnTransactions() = runBlocking {
        db.accountDao().insertAll(listOf("t1", "t2").map { AccountEntity(it, "Twin", "KZT", null, null, 5, null, false, true, false, false, "", "") })
        val ledger = LedgerRepository(db)
        listOf("t1", "t2").forEach { ledger.saveTransaction(TransactionDraft(null, TransactionType.Expense, it, "cafe", 1_000, "2026-10-26", "on $it", emptySet())) }
        val result = pull(pushedTabs())
        assertEquals(0, result.added + result.updated)
        assertEquals(listOf("t1", "t2"), db.transactionDao().getAll().filter { it.comment!!.startsWith("on ") }.sortedBy { it.comment }.map { it.accountUid })
    }

    @Test
    fun aHandTypedRowWhosePushFailed_isNotAddedAgainByTheNextPull() = runBlocking {
        settings.setLastSyncAt("2026-11-02T00:00:00.000Z")
        val tabs = pushedTabs()
        tabs.getValue("Transactions") += mutableListOf<Any?>(RowDate.serial("2026-11-01"), "Income", "Dollars", "", "", 50.0, "USD", 25000.0, "gift")
        assertEquals(1, pull(tabs).added)
        assertEquals(0, pull(tabs).added) // the sheet still has no UID on that row: the earlier push did not finish
        assertEquals(1, db.transactionDao().getAll().count { it.comment == "gift" })
    }

    @Test
    fun editsMadeOnAPc_comeIn_andKeepWhatTheSheetDoesNotShow() = runBlocking {
        val tabs = pushedTabs()
        val tx = tabs.getValue("Transactions")
        val uid = tx[1][11] as String
        tx[1][5] = 1234.56 // Amount
        tx[1][3] = "" // no category
        tx[1][4] = "trip" // a new tag
        tx += mutableListOf<Any?>(RowDate.serial("2026-11-01"), "Income", "Dollars", "", "", 50.0, "USD", 25000.0, "gift") // typed by hand, no UID
        val result = pull(tabs)

        assertEquals(2, result.added) // the hand-typed transaction and the new tag
        assertEquals(1, result.updated)
        val edited = db.transactionDao().get(uid)!!
        assertEquals(123_456L, edited.amountInAccountCurrency)
        assertEquals(null, edited.categoryUid)
        assertEquals("2026-11-03T00:00:00.000Z", edited.modified)
        assertEquals(1, db.transactionDao().tagUidsOf(uid).size)
        val manual = db.transactionDao().getAll().single { it.comment == "gift" }
        assertEquals(TransactionType.Income, manual.type)
        assertEquals("usd", manual.accountUid)
        assertEquals(5_000L, manual.amountInAccountCurrency)
        assertEquals(2_500_000L, manual.amountInDefaultCurrency)
        assertEquals(manual.date, "2026-11-01")
    }

    @Test
    fun aRowListedInDeleted_isTombstoned_andUnknownAccountsAreSkippedWithAWarning() = runBlocking {
        val tabs = pushedTabs()
        val tx = tabs.getValue("Transactions")
        val gone = tx.removeAt(2)[11] as String
        tabs.getValue("_deleted") += mutableListOf<Any?>("Transaction", gone, "2026-11-02T00:00:00.000Z")
        tx += mutableListOf<Any?>(RowDate.serial("2026-11-01"), "Expense", "Nowhere", "", "", 1.0)
        val result = pull(tabs)

        assertEquals(1, result.deleted)
        assertTrue(db.transactionDao().get(gone)!!.isRemoved)
        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings.single().contains("skipped"))
    }

    @Test
    fun phoneEditsAndDeletesMadeAfterTheLastSync_survivePullingAnOlderSheet() = runBlocking {
        val tabs = pushedTabs() // the sheet as of the last sync
        settings.setLastSyncAt("2000-01-01T00:00:00.000Z") // every phone row below was modified after it
        val all = db.transactionDao().getAll()
        val edited = all[0]
        val deleted = all[1]
        LedgerRepository(db).saveTransaction(TransactionDraft(edited.uid, edited.type, "main", "cafe", 777_00L, edited.date, "mine", emptySet()))
        LedgerRepository(db).deleteTransaction(deleted.uid)
        val result = pull(tabs)

        assertEquals(0, result.updated + result.added)
        assertEquals(77_700L, db.transactionDao().get(edited.uid)!!.amountInAccountCurrency)
        assertTrue(db.transactionDao().get(deleted.uid)!!.isRemoved)
    }

    @Test
    fun aDeletedCategory_keepsItsOldTransactions() = runBlocking {
        LedgerRepository(db).deleteCategory("cafe")
        val tabs = pushedTabs()
        val result = pull(tabs)

        assertEquals(emptyList<String>(), result.warnings)
        assertTrue(db.transactionDao().getAll().all { it.categoryUid == "cafe" })
    }

    @Test
    fun aNewerSchemaIsRefused() = runBlocking<Unit> {
        val tabs = pushedTabs()
        tabs.getValue("_meta")[1][1] = 99
        val error = runCatching { pull(tabs) }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("newer version"))
    }

    @Test
    fun readTabs_asksForTypedValuesOfEveryTabsOwnColumns() = runBlocking {
        val transport = FakeTransport { ok("""{"valueRanges":[{"values":[["Date"],[46000]]},{}]}""") }
        val out = SheetsApi(transport, { "token" }).readTabs("sid", listOf(SheetLayout.TRANSACTIONS, SheetLayout.TAGS))
        val url = java.net.URLDecoder.decode(transport.requests.single().url, "UTF-8")
        assertTrue(url, url.contains("ranges='Transactions'!A:L&ranges='Tags'!A:B") && url.contains("valueRenderOption=UNFORMATTED_VALUE"))
        assertEquals(listOf(listOf("Date"), listOf(46000)), out["Transactions"])
        assertEquals(emptyList<List<Any?>>(), out["Tags"])
    }
}

private object RowDate {
    fun serial(iso: String) = SheetRows.dateSerial(iso)
}
