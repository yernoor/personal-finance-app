package dev.yernur.moneytracker.data.sheets

import androidx.room.withTransaction
import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.TransactionTagEntity
import dev.yernur.moneytracker.data.nowUtc

class PullResult(val added: Int, val updated: Int, val deleted: Int, val warnings: List<String>)

/**
 * Imports the linked spreadsheet into the phone's database: the sheet wins for the rows it has, rows only the phone has stay,
 * and transactions/transfers listed in `_deleted` (and not in their tab) are tombstoned. Accounts, categories and tags are never deleted.
 */
class SheetsPull(
    private val db: AppDatabase,
    private val api: SheetsApi,
    private val settings: AppSettings,
    private val now: () -> String = ::nowUtc,
) {
    suspend fun pull(onProgress: (String) -> Unit = {}): PullResult {
        val id = requireNotNull(settings.spreadsheetId.value) { "No spreadsheet is linked yet" }
        onProgress("Reading the sheet")
        val tabs = api.readTabs(id, SheetLayout.ALL)
        val version = tabs[SheetLayout.META.title].orEmpty().firstOrNull { it.firstOrNull() == "schemaVersion" }?.getOrNull(1)?.toString()?.toDoubleOrNull()?.toInt()
        if (version != null && version > SheetLayout.SCHEMA_VERSION) {
            throw IllegalStateException("The sheet was written by a newer version of the app (schema $version). Update the app first.")
        }

        onProgress("Comparing with this phone")
        val stamp = now()
        val changes = db.withTransaction {
            val local = LocalSnapshot(
                db.accountDao().getAll(), db.categoryDao().getAll(), db.tagDao().getAll(), db.transactionDao().getAll(),
                db.transactionDao().getAllTagLinks(), db.transferDao().getAll(),
            )
            SheetParser(local, stamp, settings.lastSyncAt.value).parse(tabs).also { apply(it, stamp) }
        }
        return PullResult(changes.added, changes.updated, changes.deletedTransactions.size + changes.deletedTransfers.size, changes.warnings)
    }

    /** Parents before children (foreign keys). Upserts, not replaces: a replace would cascade into rows that still point at the old one. */
    private suspend fun apply(c: SheetChanges, stamp: String) {
        c.accounts.forEach { db.accountDao().upsert(it) }
        c.categories.forEach { db.categoryDao().upsert(it) }
        c.tags.forEach { db.tagDao().upsert(it) }
        c.transactions.forEach { t ->
            db.transactionDao().upsert(t)
            db.transactionDao().clearTags(t.uid)
            db.transactionDao().insertTagLinks(c.transactionTags[t.uid].orEmpty().map { TransactionTagEntity(t.uid, it) })
        }
        c.transfers.forEach { db.transferDao().upsert(it) }
        c.deletedTransactions.forEach { uid -> db.transactionDao().get(uid)?.let { db.transactionDao().upsert(it.copy(isRemoved = true, modified = stamp)) } }
        c.deletedTransfers.forEach { uid -> db.transferDao().get(uid)?.let { db.transferDao().upsert(it.copy(isRemoved = true, modified = stamp)) } }
    }
}
