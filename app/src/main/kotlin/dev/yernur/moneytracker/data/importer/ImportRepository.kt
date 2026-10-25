package dev.yernur.moneytracker.data.importer

import androidx.room.withTransaction
import dev.yernur.moneytracker.data.db.AppDatabase

/** Writes an [ImportResult] in one transaction, parents before children (foreign keys). Existing uids are replaced. */
class ImportRepository(private val db: AppDatabase) {
    suspend fun store(result: ImportResult) = db.withTransaction {
        db.accountDao().insertAll(result.accounts)
        db.categoryDao().insertAll(result.categories)
        db.tagDao().insertAll(result.tags)
        db.transactionDao().insertAll(result.transactions)
        db.transactionDao().insertTagLinks(result.transactionTags)
        db.transferDao().insertAll(result.transfers)
        db.reminderDao().insertAll(result.reminders)
        db.rateDao().insertAll(result.rates)
    }
}
