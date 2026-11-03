package dev.yernur.moneytracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Money is Long minor units (x100), as in the .mmbackup. Dates are ISO-8601 strings
// (`created`/`modified` UTC with millis, `date` as YYYY-MM-DD). Rows are never hard-deleted:
// `isRemoved` is the tombstone that sync propagates (docs/SYNC_DESIGN.md).

enum class TransactionType { Income, Expense }

/** Whether a category counts toward the overall spending projection: Auto = by the "fewer than 2 operations a month" rule. */
enum class ProjectionMode { Auto, Include, Exclude }

@Entity(tableName = "account")
data class AccountEntity(
    @PrimaryKey val uid: String,
    val title: String,
    val currencyCode: String,
    val icon: String?,
    val color: Int?,
    val position: Int?,
    val comment: String?,
    /** Excluded from the headline Total (eye-off icon). */
    val ignoreInBalance: Boolean,
    val isActive: Boolean,
    val isArchived: Boolean,
    val isFavorite: Boolean,
    val created: String,
    val modified: String,
    val isRemoved: Boolean = false,
)

@Entity(tableName = "category")
data class CategoryEntity(
    @PrimaryKey val uid: String,
    val title: String,
    val type: TransactionType,
    val icon: String?,
    val color: Int?,
    val useCount: Int,
    val lastUse: String?,
    /** Planned outlay per month in default-currency minor units; null = no limit. */
    val limitAmount: Long?,
    val position: Int?,
    val isArchived: Boolean,
    val created: String,
    val modified: String,
    val isRemoved: Boolean = false,
    /** Added in schema 2. Warn when a projection passes this share of [limitAmount]; null = the global setting. */
    val warnThresholdPercent: Int? = null,
    @ColumnInfo(defaultValue = "Auto") val projectionMode: ProjectionMode = ProjectionMode.Auto,
)

@Entity(tableName = "tag")
data class TagEntity(
    @PrimaryKey val uid: String,
    val name: String,
    val useCount: Int,
    val lastUse: String?,
    val created: String,
    val modified: String,
    val isRemoved: Boolean = false,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(AccountEntity::class, parentColumns = ["uid"], childColumns = ["accountUid"]),
        ForeignKey(CategoryEntity::class, parentColumns = ["uid"], childColumns = ["categoryUid"]),
    ],
    indices = [Index("accountUid"), Index("categoryUid"), Index("date")],
)
data class TransactionEntity(
    @PrimaryKey val uid: String,
    val type: TransactionType,
    val accountUid: String,
    /** Nullable: 21 imported transactions have no category link (2026-10-25 export). */
    val categoryUid: String?,
    val amountInDefaultCurrency: Long?,
    val amountInRealCurrency: Long?,
    val realCurrencyCode: String?,
    /**
     * Amount in the account's currency. The backup leaves this NULL for 60 USD rows and keeps the amount in
     * amountInRealCurrency; the importer copies it here (docs/FORMAT.md 4b). Null only if unresolvable.
     */
    val amountInAccountCurrency: Long?,
    val accountCurrencyCode: String?,
    val date: String,
    val comment: String?,
    val created: String,
    val modified: String,
    val isRemoved: Boolean = false,
)

@Entity(
    tableName = "transaction_tag",
    primaryKeys = ["transactionUid", "tagUid"],
    foreignKeys = [
        ForeignKey(TransactionEntity::class, parentColumns = ["uid"], childColumns = ["transactionUid"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TagEntity::class, parentColumns = ["uid"], childColumns = ["tagUid"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagUid")],
)
data class TransactionTagEntity(val transactionUid: String, val tagUid: String)

@Entity(
    tableName = "transfer",
    foreignKeys = [
        ForeignKey(AccountEntity::class, parentColumns = ["uid"], childColumns = ["fromAccountUid"]),
        ForeignKey(AccountEntity::class, parentColumns = ["uid"], childColumns = ["toAccountUid"]),
    ],
    indices = [Index("fromAccountUid"), Index("toAccountUid"), Index("date")],
)
data class TransferEntity(
    @PrimaryKey val uid: String,
    val fromAccountUid: String,
    val toAccountUid: String,
    val fromAmount: Long,
    val fromCurrencyCode: String,
    /** Backups fall back to fromAmount when null. */
    val toAmount: Long?,
    val toCurrencyCode: String?,
    val date: String,
    val comment: String?,
    val created: String,
    val modified: String,
    val isRemoved: Boolean = false,
)

enum class ReminderKind { Reminding, RegularPayments }

/** Reminders and regular payments live in one table in the backup. */
@Entity(tableName = "reminder")
data class ReminderEntity(
    @PrimaryKey val uid: String,
    val kind: ReminderKind,
    val title: String,
    val text: String?,
    /** Values seen: "Day", "Month". Others (custom) unverified. */
    val period: String,
    val transactionType: TransactionType?,
    val startDate: String,
    val endDate: String?,
    /** "HH:mm". The backup stores it unpadded ("10:0"); the importer normalizes. */
    val time: String,
    val enabled: Boolean,
    val amount: Long?,
    val addAutomatically: Boolean,
    val customPeriodData: String?,
    val created: String,
    val modified: String,
    val isRemoved: Boolean = false,
)

@Entity(tableName = "rate", primaryKeys = ["fromCurrencyCode", "toCurrencyCode"])
data class RateEntity(
    val fromCurrencyCode: String,
    val toCurrencyCode: String,
    /** Exchange rate, not money: Double is fine. */
    val value: Double,
    val modified: String,
)
