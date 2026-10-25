package dev.yernur.moneytracker.data.sheets

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SheetsApiTest {
    private val sleeps = mutableListOf<Long>()
    private val tokensAsked = mutableListOf<Boolean>()

    private fun api(transport: HttpTransport) = SheetsApi(
        transport, { forceRefresh -> tokensAsked += forceRefresh; if (forceRefresh) "fresh" else "token" },
        sleep = { sleeps += it }, jitter = { 7 },
    )

    private val created = """{"spreadsheetId":"sid","spreadsheetUrl":"https://docs.google.com/spreadsheets/d/sid/edit",
        "sheets":[{"properties":{"sheetId":0,"title":"Transactions"}},{"properties":{"sheetId":11,"title":"_deleted"}}]}"""

    @Test
    fun createSpreadsheet_sendsTheTabsInOrder_hidesTheBookkeepingOnes_andParsesTheIds() = runBlocking {
        val transport = FakeTransport { ok(created) }
        val result = api(transport).createSpreadsheet("Money Tracker", SheetLayout.ALL)

        assertEquals("sid", result.id)
        assertEquals(mapOf("Transactions" to 0, "_deleted" to 11), result.sheetIds)
        val request = transport.requests.single()
        assertEquals("https://sheets.googleapis.com/v4/spreadsheets", request.url)
        assertEquals("Bearer token", request.headers["Authorization"])
        val sheets = JSONObject(request.body!!).getJSONArray("sheets")
        assertEquals(SheetLayout.ALL.map { it.title }, (0 until sheets.length()).map { sheets.getJSONObject(it).getJSONObject("properties").getString("title") })
        val hidden = (0 until sheets.length()).filter { sheets.getJSONObject(it).getJSONObject("properties").getBoolean("hidden") }
        assertEquals(listOf("_deleted", "_meta"), hidden.map { SheetLayout.ALL[it].title })
        assertEquals(1, sheets.getJSONObject(0).getJSONObject("properties").getJSONObject("gridProperties").getInt("frozenRowCount"))
    }

    @Test
    fun writeRows_splitsIntoRequests_startsEachAtTheRightRow_andWritesRaw() = runBlocking {
        val transport = FakeTransport { ok() }
        val rows = List(3_200) { listOf("row $it", it) }
        api(transport).writeRows("sid", "Transactions", rows)

        assertEquals(3, transport.requests.size)
        val ranges = transport.requests.map { JSONObject(it.body!!).getJSONArray("data").getJSONObject(0).getString("range") }
        assertEquals(listOf("'Transactions'!A1", "'Transactions'!A1501", "'Transactions'!A3001"), ranges)
        assertEquals("RAW", JSONObject(transport.requests[0].body!!).getString("valueInputOption")) // formulas in comments stay text
        assertTrue(transport.requests[0].url.endsWith("/spreadsheets/sid/values:batchUpdate"))
        val last = JSONObject(transport.requests[2].body!!).getJSONArray("data").getJSONObject(0).getJSONArray("values")
        assertEquals(200, last.length())
        assertEquals("row 3199", last.getJSONArray(199).getString(0))
    }

    @Test
    fun tooManyRequests_isRetriedWithGrowingBackoff() = runBlocking {
        var calls = 0
        val transport = FakeTransport { if (calls++ < 2) HttpResponse(429, "{}") else ok() }
        api(transport).batchUpdate("sid", listOf(JSONObject()))
        assertEquals(3, transport.requests.size)
        assertEquals(listOf(1_007L, 2_007L), sleeps) // 2^0 s and 2^1 s plus the jitter
    }

    @Test
    fun backoffIsCapped_andRetriesAreLimited() = runBlocking<Unit> {
        val transport = FakeTransport { HttpResponse(503, "{}") }
        val error = assertThrows(SheetsException::class.java) { runBlocking { api(transport).batchUpdate("sid", listOf(JSONObject())) } }
        assertEquals(503, error.code)
        assertEquals(SheetsApi.MAX_RETRIES + 1, transport.requests.size)
        assertEquals(listOf(1_007L, 2_007L, 4_007L, 8_007L, 16_007L, 32_007L), sleeps)
    }

    @Test
    fun anExpiredToken_isRefreshedOnce() = runBlocking {
        var calls = 0
        val transport = FakeTransport { if (calls++ == 0) HttpResponse(401, "{}") else ok() }
        api(transport).batchUpdate("sid", listOf(JSONObject()))
        assertEquals(listOf(false, true), tokensAsked)
        assertEquals(listOf("Bearer token", "Bearer fresh"), transport.requests.map { it.headers["Authorization"] })

        val stubborn = FakeTransport { HttpResponse(401, """{"error":{"message":"Invalid Credentials"}}""") }
        val error = assertThrows(SheetsException::class.java) { runBlocking { api(stubborn).batchUpdate("sid", listOf(JSONObject())) } }
        assertEquals(401, error.code)
        assertEquals(2, stubborn.requests.size) // one refresh, not a loop
    }

    @Test
    fun otherErrors_failAtOnce_withGooglesMessage() = runBlocking<Unit> {
        val transport = FakeTransport { HttpResponse(403, """{"error":{"code":403,"message":"The caller does not have permission"}}""") }
        val error = assertThrows(SheetsException::class.java) { runBlocking { api(transport).createSpreadsheet("t", SheetLayout.ALL) } }
        assertEquals(403, error.code)
        assertEquals("The caller does not have permission", error.message)
        assertEquals(1, transport.requests.size)
        assertTrue(sleeps.isEmpty())

        val plain = FakeTransport { HttpResponse(400, "not json") }
        assertEquals("HTTP 400", assertThrows(SheetsException::class.java) { runBlocking { api(plain).batchUpdate("sid", listOf(JSONObject())) } }.message)
    }
}
