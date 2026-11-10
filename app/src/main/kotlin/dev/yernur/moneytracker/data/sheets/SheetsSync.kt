package dev.yernur.moneytracker.data.sheets

import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.nowUtc
import kotlinx.coroutines.flow.first

class SpreadsheetLink(val id: String, val url: String)

/**
 * Milestone 6: put everything into one spreadsheet. It reuses the one a failed earlier try left behind (remembered as pending) or, best effort,
 * one this app made before that Drive can find by name, and overwrites our columns; it creates a new one only when there is none. Two-way sync comes in milestone 7.
 */
class SheetsSync(
    private val db: AppDatabase,
    private val api: SheetsApi,
    private val settings: AppSettings,
    private val now: () -> String = ::nowUtc,
) {
    suspend fun pushAll(onProgress: (String) -> Unit = {}): SpreadsheetLink {
        pullIfPhoneIsEmpty(onProgress)
        onProgress("Reading your data")
        val stamp = now() // taken before reading: a phone edit made during the push counts as not yet synced
        val tabs = SheetRows.build(
            db.accountDao().getAll(), db.categoryDao().getAll(), db.tagDao().getAll(), db.transactionDao().getAll(),
            db.transactionDao().getAllTagLinks(), db.transferDao().getAll(),
            db.accountDao().observeBalances().first().associate { it.uid to it.balance },
            settings.deviceId(), stamp,
        )

        val rowCounts = tabs.associate { it.spec.title to it.rows.size + 1 }
        val created = reuse(rowCounts, onProgress) ?: run {
            onProgress("Creating the spreadsheet")
            api.createSpreadsheet(TITLE, SheetLayout.ALL, rowCounts).also { settings.setPendingSpreadsheet(it.id) }
        }

        for (tab in tabs) {
            onProgress("Uploading ${tab.spec.title} (${tab.rows.size} rows)")
            api.writeRows(created.id, tab.spec.title, listOf(tab.spec.headers) + tab.rows)
        }

        onProgress("Formatting")
        api.batchUpdate(created.id, SheetFormats.requests(SheetLayout.ALL, created.sheetIds))

        settings.setSpreadsheet(created.id, created.url)
        settings.setPendingSpreadsheet(null)
        settings.setLastSyncAt(stamp)
        return SpreadsheetLink(created.id, created.url)
    }

    /**
     * A push empties the sheet first, so an empty phone (new phone, reinstall) must not push over a sheet that already has the data:
     * it takes the sheet's data in before anything is cleared. A failure here stops the push with the sheet untouched.
     */
    private suspend fun pullIfPhoneIsEmpty(onProgress: (String) -> Unit) {
        val empty = db.transactionDao().getAll().none { !it.isRemoved } && db.transferDao().getAll().none { !it.isRemoved }
        if (!empty) return
        val id = settings.pendingSpreadsheetId.value ?: settings.spreadsheetId.value ?: findByName() ?: return
        val existing = try {
            api.describe(id)
        } catch (e: SheetsException) {
            return // gone or not ours: reuse() will make a new one
        }
        onProgress("Phone is empty: reading the sheet first")
        settings.setSpreadsheet(existing.id, existing.url)
        SheetsPull(db, api, settings, now).pull(onProgress)
    }

    /** The spreadsheet to overwrite, fitted to the data and emptied, or null when a new one has to be made. */
    private suspend fun reuse(rowCounts: Map<String, Int>, onProgress: (String) -> Unit): CreatedSpreadsheet? {
        val id = settings.pendingSpreadsheetId.value ?: settings.spreadsheetId.value ?: findByName() ?: return null
        val existing = try {
            api.describe(id)
        } catch (e: SheetsException) {
            settings.setPendingSpreadsheet(null) // deleted, or not ours any more
            return null
        }
        onProgress("Refreshing the existing sheet")
        settings.setPendingSpreadsheet(existing.id)
        val fitted = api.fitTabs(existing, SheetLayout.ALL, rowCounts)
        api.clear(fitted.id, SheetLayout.ALL.map { it.columnsRange })
        return fitted
    }

    /** Needs the Drive API enabled in the Cloud project; without it (or without access) we just make a new sheet. */
    private suspend fun findByName(): String? = try {
        api.findSpreadsheet(TITLE)
    } catch (e: SheetsException) {
        null
    }

    private companion object {
        const val TITLE = "Money Tracker"
    }
}
