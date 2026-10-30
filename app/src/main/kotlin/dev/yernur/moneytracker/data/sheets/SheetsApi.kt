package dev.yernur.moneytracker.data.sheets

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** The API refused a request for good (bad request, no access, ...) or kept failing after the retries. */
class SheetsException(val code: Int, message: String) : Exception(message)

class CreatedSpreadsheet(val id: String, val url: String, val sheetIds: Map<String, Int>)

/** Gives the OAuth access token; [forceRefresh] asks for a new one after the API rejected the old one. */
fun interface TokenProvider {
    suspend fun token(forceRefresh: Boolean): String
}

/**
 * The few Sheets API v4 calls this app needs. Retries follow Google's guidance (docs: usage limits): HTTP 429 and 5xx wait
 * 2^n seconds plus up to a second of jitter, capped at 32 s; a 401 gets one fresh token.
 */
class SheetsApi(
    private val transport: HttpTransport,
    private val tokens: TokenProvider,
    private val sleep: suspend (millis: Long) -> Unit = { delay(it) },
    private val jitter: () -> Long = { (Math.random() * 1000).toLong() },
) {
    /**
     * [rowCounts] is the number of rows (header included) each tab will hold. The grid must be that big up front:
     * values:batchUpdate does not grow it, so a write past the default grid fails ("exceeds grid limits", seen on the first live push).
     */
    suspend fun createSpreadsheet(title: String, tabs: List<TabSpec>, rowCounts: Map<String, Int> = emptyMap()): CreatedSpreadsheet {
        val body = JSONObject()
            .put("properties", JSONObject().put("title", title))
            .put("sheets", JSONArray(tabs.map { tab ->
                val grid = JSONObject().put("frozenRowCount", 1)
                rowCounts[tab.title]?.let { grid.put("rowCount", maxOf(it + SPARE_ROWS, MIN_ROWS)) }
                JSONObject().put("properties", JSONObject().put("title", tab.title).put("hidden", tab.hidden).put("gridProperties", grid))
            }))
        val response = call("POST", "/spreadsheets", body)
        val sheetIds = response.getJSONArray("sheets").let { sheets ->
            (0 until sheets.length()).associate {
                val properties = sheets.getJSONObject(it).getJSONObject("properties")
                properties.getString("title") to properties.getInt("sheetId")
            }
        }
        return CreatedSpreadsheet(response.getString("spreadsheetId"), response.optString("spreadsheetUrl"), sheetIds)
    }

    /** Writes [rows] (numbers, strings, booleans, null) from the top-left of [tab], [rowsPerRequest] at a time. Values are stored as typed, never parsed as formulas. */
    suspend fun writeRows(spreadsheetId: String, tab: String, rows: List<List<Any?>>, rowsPerRequest: Int = ROWS_PER_REQUEST) {
        rows.chunked(rowsPerRequest).forEachIndexed { index, chunk ->
            val firstRow = index * rowsPerRequest + 1
            val body = JSONObject().put("valueInputOption", "RAW").put(
                "data",
                JSONArray().put(
                    JSONObject().put("range", "'$tab'!A$firstRow").put("majorDimension", "ROWS")
                        .put("values", JSONArray(chunk.map { row -> JSONArray(row.map { it ?: JSONObject.NULL }) })),
                ),
            )
            call("POST", "/spreadsheets/$spreadsheetId/values:batchUpdate", body)
        }
    }

    /** spreadsheets.batchUpdate with ready-made request objects (formatting). */
    suspend fun batchUpdate(spreadsheetId: String, requests: List<JSONObject>) {
        if (requests.isEmpty()) return
        call("POST", "/spreadsheets/$spreadsheetId:batchUpdate", JSONObject().put("requests", JSONArray(requests)))
    }

    private suspend fun call(method: String, path: String, body: JSONObject?): JSONObject {
        var token = tokens.token(forceRefresh = false)
        var refreshed = false
        var attempt = 0
        while (true) {
            val request = HttpRequest(
                method, BASE_URL + path,
                mapOf("Authorization" to "Bearer $token", "Content-Type" to "application/json; charset=utf-8", "Accept" to "application/json"),
                body?.toString(),
            )
            val response = withContext(Dispatchers.IO) { transport.execute(request) }
            when {
                response.code in 200..299 -> return JSONObject(response.body.ifBlank { "{}" })
                response.code == 401 && !refreshed -> {
                    refreshed = true
                    token = tokens.token(forceRefresh = true)
                }
                (response.code == 429 || response.code >= 500) && attempt < MAX_RETRIES -> {
                    sleep(minOf(1L shl attempt, MAX_BACKOFF_SECONDS) * 1000 + jitter())
                    attempt++
                }
                else -> throw SheetsException(response.code, errorMessage(response))
            }
        }
    }

    private fun errorMessage(response: HttpResponse): String = try {
        JSONObject(response.body).getJSONObject("error").getString("message")
    } catch (e: Exception) {
        "HTTP ${response.code}"
    }

    companion object {
        const val BASE_URL = "https://sheets.googleapis.com/v4"
        /** Docs recommend payloads up to about 2 MB; 1,500 rows of 12 short cells is far below that. */
        const val ROWS_PER_REQUEST = 1_500
        const val SPARE_ROWS = 500 // room to type new rows by hand on a PC
        const val MIN_ROWS = 2 // a frozen header needs at least one more row
        const val MAX_RETRIES = 6
        const val MAX_BACKOFF_SECONDS = 32L
    }
}
