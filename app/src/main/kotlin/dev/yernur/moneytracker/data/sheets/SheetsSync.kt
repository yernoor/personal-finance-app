package dev.yernur.moneytracker.data.sheets

import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.nowUtc
import kotlinx.coroutines.flow.first

class SpreadsheetLink(val id: String, val url: String)

/** Milestone 6: create the spreadsheet and upload everything once. Two-way sync comes in milestone 7. */
class SheetsSync(
    private val db: AppDatabase,
    private val api: SheetsApi,
    private val settings: AppSettings,
    private val now: () -> String = ::nowUtc,
) {
    suspend fun initialPush(onProgress: (String) -> Unit = {}): SpreadsheetLink {
        onProgress("Reading your data")
        val tabs = SheetRows.build(
            db.accountDao().getAll(), db.categoryDao().getAll(), db.tagDao().getAll(), db.transactionDao().getAll(),
            db.transactionDao().getAllTagLinks(), db.transferDao().getAll(),
            db.accountDao().observeBalances().first().associate { it.uid to it.balance },
            settings.deviceId(), now(),
        )

        onProgress("Creating the spreadsheet")
        val created = api.createSpreadsheet(TITLE, SheetLayout.ALL, tabs.associate { it.spec.title to it.rows.size + 1 })

        for (tab in tabs) {
            onProgress("Uploading ${tab.spec.title} (${tab.rows.size} rows)")
            api.writeRows(created.id, tab.spec.title, listOf(tab.spec.headers) + tab.rows)
        }

        onProgress("Formatting")
        api.batchUpdate(created.id, SheetFormats.requests(SheetLayout.ALL, created.sheetIds))

        settings.setSpreadsheet(created.id, created.url)
        return SpreadsheetLink(created.id, created.url)
    }

    private companion object {
        const val TITLE = "Money Tracker"
    }
}
