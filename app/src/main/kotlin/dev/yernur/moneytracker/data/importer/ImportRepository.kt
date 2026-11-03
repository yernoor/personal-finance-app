package dev.yernur.moneytracker.data.importer

import androidx.room.withTransaction
import dev.yernur.moneytracker.data.db.AppDatabase

/**
 * Writes an [ImportResult] in one transaction, parents before children (foreign keys). A row already on the phone is replaced only when the
 * backup's copy has a newer `modified`, so importing an older backup never undoes later edits or deletes made here.
 */
class ImportRepository(private val db: AppDatabase) {
    suspend fun store(result: ImportResult) = db.withTransaction {
        result.accounts.newerThan(db.accountDao().getAll(), { it.uid }, { it.modified }).forEach { db.accountDao().upsert(it) }
        result.categories.newerThan(db.categoryDao().getAll(), { it.uid }, { it.modified }).forEach { db.categoryDao().upsert(it) }
        result.tags.newerThan(db.tagDao().getAll(), { it.uid }, { it.modified }).forEach { db.tagDao().upsert(it) }
        val transactions = result.transactions.newerThan(db.transactionDao().getAll(), { it.uid }, { it.modified })
        transactions.forEach { db.transactionDao().upsert(it); db.transactionDao().clearTags(it.uid) }
        val written = transactions.map { it.uid }.toSet()
        db.transactionDao().insertTagLinks(result.transactionTags.filter { it.transactionUid in written })
        result.transfers.newerThan(db.transferDao().getAll(), { it.uid }, { it.modified }).forEach { db.transferDao().upsert(it) }
        db.reminderDao().insertAll(result.reminders)
        db.rateDao().insertAll(result.rates)
    }

    /** The rows that are new here or newer than the copy here (timestamps are UTC ISO-8601 with millis, so text order is time order). */
    private fun <T> List<T>.newerThan(existing: List<T>, uid: (T) -> String, modified: (T) -> String): List<T> {
        val have = existing.associateBy(uid)
        return filter { row -> have[uid(row)]?.let { modified(row) > modified(it) } ?: true }
    }
}
