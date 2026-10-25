package dev.yernur.moneytracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

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
    version = 2,
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

/** Adds the per-category warning threshold and projection mode (milestone 5). */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE category ADD COLUMN warnThresholdPercent INTEGER")
        connection.execSQL("ALTER TABLE category ADD COLUMN projectionMode TEXT NOT NULL DEFAULT 'Auto'")
    }
}
