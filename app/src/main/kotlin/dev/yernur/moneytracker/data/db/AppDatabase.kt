package dev.yernur.moneytracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TagEntity::class,
        TransactionEntity::class,
        TransactionTagEntity::class,
        TransferEntity::class,
        ReminderEntity::class,
        RateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun tagDao(): TagDao
    abstract fun transactionDao(): TransactionDao
    abstract fun transferDao(): TransferDao
    abstract fun reminderDao(): ReminderDao
    abstract fun rateDao(): RateDao
}
