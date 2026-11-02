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

    private val tabTitles = SheetLayout.ALL.map { it.title }

    /** What spreadsheets.get answers for a spreadsheet whose tabs each have [rows] grid rows (tabs in [without] are missing). */
    private fun described(rows: Int, without: Set<String> = emptySet()): String {
        val sheets = tabTitles.filter { it !in without }.mapIndexed { i, t -> """{"properties":{"sheetId":${i * 10},"title":"$t","gridProperties":{"rowCount":$rows}}}""" }
        return """{"spreadsheetUrl":"https://docs.google.com/spreadsheets/d/old/edit","sheets":[${sheets.joinToString(",")}]}"""
    }

    /**
     * A Sheets/Drive API that accepts everything and numbers the tabs it is asked to create.
     * [driveHit] is the spreadsheet id Drive finds by name (none by default); [describe] answers a read of an existing spreadsheet.
     */
    private fun fakeGoogle(
        driveHit: String? = null,
        describe: (HttpRequest) -> HttpResponse = { ok(described(rows = 5)) },
        failOn: (HttpRequest) -> Boolean = { false },
    ) = FakeTransport { request ->
        when {
            failOn(request) -> HttpResponse(403, """{"error":{"message":"no access"}}""")
            request.url.startsWith(SheetsApi.DRIVE_URL) -> ok(if (driveHit != null) """{"files":[{"id":"$driveHit"}]}""" else """{"files":[]}""")
            request.method == "GET" -> describe(request)
            request.url.endsWith("/spreadsheets") -> {
                val titles = JSONObject(request.body!!).getJSONArray("sheets").let { s -> (0 until s.length()).map { s.getJSONObject(it).getJSONObject("properties").getString("title") } }
                val sheets = titles.mapIndexed { i, t -> """{"properties":{"sheetId":${i * 10},"title":"$t"}}""" }.joinToString(",")
                ok("""{"spreadsheetId":"sid","spreadsheetUrl":"https://docs.google.com/spreadsheets/d/sid/edit","sheets":[$sheets]}""")
            }
            else -> ok()
        }
    }

    private fun FakeTransport.sheetsUrls() = requests.filter { !it.url.startsWith(SheetsApi.DRIVE_URL) }.map { it.url.removePrefix(SheetsApi.BASE_URL) }

    private fun sync(transport: FakeTransport) = SheetsSync(db, SheetsApi(transport, { "token" }, sleep = {}, jitter = { 0 }), settings) { "2026-10-26T00:00:00.000Z" }

    @Test
    fun pushAll_createsTheSheet_uploadsEveryTabWithItsHeader_formats_andRemembersTheLink() = runBlocking {
        val transport = fakeGoogle()
        val link = sync(transport).pushAll { progress += it }

        assertEquals("sid", link.id)
        assertEquals("sid", settings.spreadsheetId.value)
        assertEquals("https://docs.google.com/spreadsheets/d/sid/edit", settings.spreadsheetUrl.value)

        val urls = transport.sheetsUrls()
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
        sync(transport).pushAll()
        val transactions = JSONObject(transport.requests.first { it.url.endsWith("values:batchUpdate") }.body!!).getJSONArray("data").getJSONObject(0).getJSONArray("values")
        val net = (1 until transactions.length()).sumOf { transactions.getJSONArray(it).getBigDecimal(7) }
        assertEquals("-6000.00", net.toPlainString()) // 1,000 + 2,000 + 3,000 KZT of expenses, all negative
    }

    @Test
    fun aFailureLeavesNothingRemembered_andSaysWhy() = runBlocking<Unit> {
        val denied = fakeGoogle(failOn = { it.url.endsWith("/spreadsheets") })
        val error = assertThrows(SheetsException::class.java) { runBlocking { sync(denied).pushAll() } }
        assertEquals("no access", error.message)
        assertNull(settings.spreadsheetId.value)

        val midway = fakeGoogle(failOn = { it.url.contains("values:batchUpdate") })
        assertThrows(SheetsException::class.java) { runBlocking { sync(midway).pushAll() } }
        assertNull(settings.spreadsheetId.value) // only a completed push is remembered as the owner's sheet
        assertEquals("sid", settings.pendingSpreadsheetId.value) // but the half-filled one is kept to be filled next time
    }

    @Test
    fun aRetryAfterAFailure_fillsTheSameSheet_insteadOfMakingAnother() = runBlocking {
        settings.setPendingSpreadsheet("sid")
        val transport = fakeGoogle(describe = { ok(described(rows = 5)) })
        val link = sync(transport).pushAll()

        assertEquals("sid", link.id)
        assertTrue(transport.requests.none { it.method == "POST" && it.url.endsWith("/spreadsheets") }) // nothing created
        assertNull(settings.pendingSpreadsheetId.value)
        assertEquals("sid", settings.spreadsheetId.value)
    }

    @Test
    fun aSheetWithTheSameNameInDrive_isReused_andOnlyOurColumnsAreCleared() = runBlocking {
        val transport = fakeGoogle(driveHit = "old", describe = { ok(described(rows = 5000)) })
        val link = sync(transport).pushAll()

        assertEquals("old", link.id)
        assertTrue(transport.requests.none { it.method == "POST" && it.url.endsWith("/spreadsheets") })
        val query = java.net.URLDecoder.decode(transport.requests.first().url, "UTF-8")
        assertTrue(query, query.contains("name = 'Money Tracker'") && query.contains("trashed = false"))
        val cleared = JSONObject(transport.requests.first { it.url.endsWith("values:batchClear") }.body!!).getJSONArray("ranges")
        assertEquals(SheetLayout.ALL.map { it.columnsRange }, (0 until cleared.length()).map(cleared::getString))
        assertEquals("'Transactions'!A:L", cleared.getString(0)) // a column added by hand at M stays
        // the clear happens before any value is written
        val order = transport.requests.map { it.url.substringAfterLast("/") }
        assertTrue(order.indexOf("values:batchClear") < order.indexOf("values:batchUpdate"))
    }

    @Test
    fun anExistingSheetThatIsTooShortOrMissingATab_isFittedBeforeTheUpload() = runBlocking {
        val transport = fakeGoogle(driveHit = "old", describe = { ok(described(rows = 5, without = setOf("Tags"))) })
        sync(transport).pushAll()

        val fit = JSONObject(transport.requests.first { it.url.endsWith("old:batchUpdate") }.body!!).getJSONArray("requests")
        val kinds = (0 until fit.length()).map { fit.getJSONObject(it).keys().next() }
        assertTrue(kinds.contains("addSheet")) // the missing Tags tab
        assertTrue(kinds.contains("updateSheetProperties")) // grids of 5 rows are too short for the data plus spare room
    }

    @Test
    fun whenDriveCannotBeSearchedOrTheSheetIsGone_aNewSheetIsMade() = runBlocking {
        val noDrive = fakeGoogle(failOn = { it.url.startsWith(SheetsApi.DRIVE_URL) })
        assertEquals("sid", sync(noDrive).pushAll().id)
        assertTrue(noDrive.requests.any { it.method == "POST" && it.url.endsWith("/spreadsheets") })

        settings.forgetSpreadsheet()
        settings.setPendingSpreadsheet("deleted")
        val gone = fakeGoogle(describe = { HttpResponse(404, """{"error":{"message":"not found"}}""") })
        assertEquals("sid", sync(gone).pushAll().id)
        assertTrue(gone.requests.any { it.method == "POST" && it.url.endsWith("/spreadsheets") })
    }
}

private fun org.json.JSONArray.getBigDecimal(index: Int) = java.math.BigDecimal(get(index).toString())
