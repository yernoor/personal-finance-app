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
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SheetsSyncTest {
    private lateinit var db: AppDatabase
    private val settings = AppSettings()
    private val progress = mutableListOf<String>()

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        db.accountDao().insertAll(
            listOf(
                AccountEntity("main", "Main", "KZT", null, null, 0, null, false, true, false, false, "", ""),
                AccountEntity("usd", "Dollars", "USD", null, null, 1, null, false, true, false, false, "", ""),
            )
        )
        db.categoryDao().insertAll(listOf(CategoryEntity("cafe", "Кафе", TransactionType.Expense, null, null, 0, null, null, 0, false, "", "")))
        val ledger = LedgerRepository(db)
        repeat(3) { ledger.saveTransaction(TransactionDraft(null, TransactionType.Expense, "main", "cafe", 100_000L * (it + 1), "2026-10-2${it + 1}", "c$it", emptySet())) }
        ledger.saveTransfer(TransferDraft(null, "main", "usd", 450_000, 1_000, "2026-10-25", null))
    }

    @After
    fun tearDown() = db.close()

    /** A Sheets API that accepts everything and numbers the tabs it is asked to create. */
    private fun fakeGoogle(failOn: (HttpRequest) -> Boolean = { false }) = FakeTransport { request ->
        when {
            failOn(request) -> HttpResponse(403, """{"error":{"message":"no access"}}""")
            request.url.endsWith("/spreadsheets") -> {
                val titles = JSONObject(request.body!!).getJSONArray("sheets").let { s -> (0 until s.length()).map { s.getJSONObject(it).getJSONObject("properties").getString("title") } }
                val sheets = titles.mapIndexed { i, t -> """{"properties":{"sheetId":${i * 10},"title":"$t"}}""" }.joinToString(",")
                ok("""{"spreadsheetId":"sid","spreadsheetUrl":"https://docs.google.com/spreadsheets/d/sid/edit","sheets":[$sheets]}""")
            }
            else -> ok()
        }
    }

    private fun sync(transport: FakeTransport) = SheetsSync(db, SheetsApi(transport, { "token" }, sleep = {}, jitter = { 0 }), settings) { "2026-10-26T00:00:00.000Z" }

    @Test
    fun initialPush_createsTheSheet_uploadsEveryTabWithItsHeader_formats_andRemembersTheLink() = runBlocking {
        val transport = fakeGoogle()
        val link = sync(transport).initialPush { progress += it }

        assertEquals("sid", link.id)
        assertEquals("sid", settings.spreadsheetId.value)
        assertEquals("https://docs.google.com/spreadsheets/d/sid/edit", settings.spreadsheetUrl.value)

        val urls = transport.requests.map { it.url.removePrefix(SheetsApi.BASE_URL) }
        assertEquals(listOf("/spreadsheets") + List(7) { "/spreadsheets/sid/values:batchUpdate" } + "/spreadsheets/sid:batchUpdate", urls)

        val uploads = transport.requests.filter { it.url.endsWith("values:batchUpdate") }.map { JSONObject(it.body!!).getJSONArray("data").getJSONObject(0) }
        assertEquals(SheetLayout.ALL.map { "'${it.title}'!A1" }, uploads.map { it.getString("range") })
        val transactions = uploads[0].getJSONArray("values")
        assertEquals(SheetLayout.TRANSACTIONS.headers, (0 until transactions.length()).first().let { transactions.getJSONArray(it) }.let { row -> (0 until row.length()).map(row::getString) })
        assertEquals(1 + 3, transactions.length()) // header + 3 transactions
        assertEquals(1 + 1, uploads[1].getJSONArray("values").length()) // header + the transfer
        assertEquals("Reading your data", progress.first())
        assertTrue(progress.any { it.startsWith("Uploading Transactions (3 rows)") })
    }

    @Test
    fun theSheetTotalsMatchTheLedger() = runBlocking {
        val transport = fakeGoogle()
        sync(transport).initialPush()
        val transactions = JSONObject(transport.requests[1].body!!).getJSONArray("data").getJSONObject(0).getJSONArray("values")
        val net = (1 until transactions.length()).sumOf { transactions.getJSONArray(it).getBigDecimal(7) }
        assertEquals("-6000.00", net.toPlainString()) // 1,000 + 2,000 + 3,000 KZT of expenses, all negative
    }

    @Test
    fun aFailureLeavesNothingRemembered_andSaysWhy() = runBlocking<Unit> {
        val denied = fakeGoogle { it.url.endsWith("/spreadsheets") }
        val error = assertThrows(SheetsException::class.java) { runBlocking { sync(denied).initialPush() } }
        assertEquals("no access", error.message)
        assertNull(settings.spreadsheetId.value)

        val midway = fakeGoogle { it.url.contains("values:batchUpdate") }
        assertThrows(SheetsException::class.java) { runBlocking { sync(midway).initialPush() } }
        assertNull(settings.spreadsheetId.value) // only a completed push is remembered
    }
}

private fun org.json.JSONArray.getBigDecimal(index: Int) = java.math.BigDecimal(get(index).toString())
