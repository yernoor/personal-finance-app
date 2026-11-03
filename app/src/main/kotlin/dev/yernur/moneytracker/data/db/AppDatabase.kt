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
        FxRateEntity::class,
        ManualAssetEntity::class,
        NetWorthSnapshotEntity::class,
        GoalEntity::class,
        BondEntity::class,
    ],
    version = 5,
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
    abstract fun fxRateDao(): FxRateDao
    abstract fun manualAssetDao(): ManualAssetDao
    abstract fun netWorthSnapshotDao(): NetWorthSnapshotDao
    abstract fun goalDao(): GoalDao
    abstract fun bondDao(): BondDao
}

/** Adds the per-category warning threshold and projection mode (milestone 5). */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE category ADD COLUMN warnThresholdPercent INTEGER")
        connection.execSQL("ALTER TABLE category ADD COLUMN projectionMode TEXT NOT NULL DEFAULT 'Auto'")
    }
}

/** Adds the cached National Bank exchange rates (wealth tools). */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `fx_rate` (`currencyCode` TEXT NOT NULL, `kztPerUnit` REAL NOT NULL, `date` TEXT NOT NULL, PRIMARY KEY(`currencyCode`))")
    }
}

/** Adds manual assets/liabilities, daily net worth snapshots and goals (wealth tools). */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `manual_asset` (`uid` TEXT NOT NULL, `title` TEXT NOT NULL, `kind` TEXT NOT NULL, `currencyCode` TEXT NOT NULL, `valueMinor` INTEGER NOT NULL, `created` TEXT NOT NULL, `modified` TEXT NOT NULL, `isRemoved` INTEGER NOT NULL, PRIMARY KEY(`uid`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `net_worth_snapshot` (`date` TEXT NOT NULL, `accountsMinor` INTEGER NOT NULL, `assetsMinor` INTEGER NOT NULL, `liabilitiesMinor` INTEGER NOT NULL, PRIMARY KEY(`date`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `goal` (`uid` TEXT NOT NULL, `title` TEXT NOT NULL, `targetMinor` INTEGER NOT NULL, `targetDate` TEXT, `accountUid` TEXT, `created` TEXT NOT NULL, `modified` TEXT NOT NULL, `isRemoved` INTEGER NOT NULL, PRIMARY KEY(`uid`))")
    }
}

/** Adds bond holdings and counts them in net worth snapshots. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `bond` (`uid` TEXT NOT NULL, `title` TEXT NOT NULL, `currencyCode` TEXT NOT NULL, `faceMinor` INTEGER NOT NULL, `quantity` INTEGER NOT NULL, `costMinor` INTEGER NOT NULL, `couponRateBp` INTEGER NOT NULL, `couponsPerYear` INTEGER NOT NULL, `maturityDate` TEXT NOT NULL, `currentValueMinor` INTEGER, `created` TEXT NOT NULL, `modified` TEXT NOT NULL, `isRemoved` INTEGER NOT NULL, PRIMARY KEY(`uid`))")
        connection.execSQL("ALTER TABLE net_worth_snapshot ADD COLUMN investmentsMinor INTEGER NOT NULL DEFAULT 0")
    }
}
