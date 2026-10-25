package dev.yernur.moneytracker.data.sheets

import org.json.JSONObject

/** spreadsheets.batchUpdate requests that make the tabs look right: bold header, yyyy-mm-dd dates, fitted columns. */
object SheetFormats {
    fun requests(tabs: List<TabSpec>, sheetIds: Map<String, Int>): List<JSONObject> = tabs.flatMap { tab ->
        val sheetId = sheetIds[tab.title] ?: return@flatMap emptyList()
        buildList {
            add(
                repeatCell(
                    range(sheetId, startRow = 0, endRow = 1),
                    JSONObject().put("textFormat", JSONObject().put("bold", true)), "userEnteredFormat.textFormat.bold",
                ),
            )
            tab.dateColumn?.let {
                add(
                    repeatCell(
                        range(sheetId, startRow = 1, startColumn = it, endColumn = it + 1),
                        JSONObject().put("numberFormat", JSONObject().put("type", "DATE").put("pattern", "yyyy-mm-dd")), "userEnteredFormat.numberFormat",
                    ),
                )
            }
            if (!tab.hidden) add(
                JSONObject().put(
                    "autoResizeDimensions",
                    JSONObject().put(
                        "dimensions",
                        JSONObject().put("sheetId", sheetId).put("dimension", "COLUMNS").put("startIndex", 0).put("endIndex", tab.headers.size),
                    ),
                ),
            )
        }
    }

    private fun range(sheetId: Int, startRow: Int, endRow: Int? = null, startColumn: Int? = null, endColumn: Int? = null) =
        JSONObject().put("sheetId", sheetId).put("startRowIndex", startRow).apply {
            endRow?.let { put("endRowIndex", it) }
            startColumn?.let { put("startColumnIndex", it) }
            endColumn?.let { put("endColumnIndex", it) }
        }

    private fun repeatCell(range: JSONObject, format: JSONObject, fields: String) = JSONObject().put(
        "repeatCell", JSONObject().put("range", range).put("cell", JSONObject().put("userEnteredFormat", format)).put("fields", fields),
    )
}
