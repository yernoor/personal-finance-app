package dev.yernur.moneytracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Net movement of one account in account-currency minor units (transactions only, transfers excluded). */
data class AccountSum(val accountUid: String, val total: Long)

/** Balance of one account in its own currency: transactions plus transfers in, minus transfers out. */
data class AccountBalance(val uid: String, val balance: Long)

data class TypeTotal(val type: TransactionType, val total: Long)

/** The few columns reports need; a missing default-currency amount counts as 0. */
data class TxLite(val date: String, val type: TransactionType, val categoryUid: String?, val amount: Long)

@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<AccountEntity>)

    @Upsert
    suspend fun upsert(item: AccountEntity)

    @Query("SELECT * FROM account WHERE uid = :uid")
    suspend fun get(uid: String): AccountEntity?

    @Query("SELECT * FROM account WHERE isRemoved = 0 ORDER BY position, title")
    fun observeLive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM account")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM account")
    suspend fun getAll(): List<AccountEntity>

    @Query(
        """SELECT a.uid AS uid,
              COALESCE((SELECT SUM(CASE t.type WHEN 'Income' THEN t.amountInAccountCurrency
                                               ELSE -t.amountInAccountCurrency END)
                        FROM transactions t WHERE t.accountUid = a.uid AND t.isRemoved = 0), 0)
            + COALESCE((SELECT SUM(COALESCE(r.toAmount, r.fromAmount))
                        FROM transfer r WHERE r.toAccountUid = a.uid AND r.isRemoved = 0), 0)
            - COALESCE((SELECT SUM(r.fromAmount)
                        FROM transfer r WHERE r.fromAccountUid = a.uid AND r.isRemoved = 0), 0) AS balance
           FROM account a WHERE a.isRemoved = 0"""
    )
    fun observeBalances(): Flow<List<AccountBalance>>
}

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<CategoryEntity>)

    @Upsert
    suspend fun upsert(item: CategoryEntity)

    @Query("SELECT * FROM category WHERE uid = :uid")
    suspend fun get(uid: String): CategoryEntity?

    @Query("SELECT * FROM category WHERE isRemoved = 0 ORDER BY position, title")
    fun observeLive(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM category WHERE type = :type")
    suspend fun nextPosition(type: TransactionType): Int

    @Query("UPDATE category SET useCount = useCount + 1, lastUse = :now, modified = :now WHERE uid = :uid")
    suspend fun bumpUsage(uid: String, now: String)
}

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<TagEntity>)

    @Upsert
    suspend fun upsert(item: TagEntity)

    @Query("SELECT * FROM tag WHERE uid = :uid")
    suspend fun get(uid: String): TagEntity?

    @Query("SELECT * FROM tag WHERE isRemoved = 0")
    suspend fun getLive(): List<TagEntity>

    @Query("SELECT * FROM tag")
    suspend fun getAll(): List<TagEntity>

    @Query("SELECT * FROM tag WHERE isRemoved = 0 ORDER BY useCount DESC, name")
    fun observeLive(): Flow<List<TagEntity>>

    @Query("UPDATE tag SET useCount = useCount + 1, lastUse = :now, modified = :now WHERE uid IN (:uids)")
    suspend fun bumpUsage(uids: Collection<String>, now: String)
}

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<TransactionEntity>)

    @Upsert
    suspend fun upsert(item: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE uid = :uid")
    suspend fun get(uid: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTagLinks(items: List<TransactionTagEntity>)

    @Query("SELECT * FROM transaction_tag")
    suspend fun getAllTagLinks(): List<TransactionTagEntity>

    @Query("SELECT tagUid FROM transaction_tag WHERE transactionUid = :uid")
    suspend fun tagUidsOf(uid: String): List<String>

    @Query("DELETE FROM transaction_tag WHERE transactionUid = :uid")
    suspend fun clearTags(uid: String)

    @Query("SELECT * FROM transactions WHERE isRemoved = 0 AND date BETWEEN :from AND :to ORDER BY date DESC, created DESC")
    fun observeBetween(from: String, to: String): Flow<List<TransactionEntity>>

    @Query("SELECT type, SUM(amountInDefaultCurrency) AS total FROM transactions WHERE isRemoved = 0 AND date BETWEEN :from AND :to GROUP BY type")
    fun observeTotals(from: String, to: String): Flow<List<TypeTotal>>

    @Query("SELECT date, type, categoryUid, COALESCE(amountInDefaultCurrency, 0) AS amount FROM transactions WHERE isRemoved = 0 AND date BETWEEN :from AND :to")
    fun observeLite(from: String, to: String): Flow<List<TxLite>>

    @Query("SELECT date, type, categoryUid, COALESCE(amountInDefaultCurrency, 0) AS amount FROM transactions WHERE isRemoved = 0 AND date BETWEEN :from AND :to")
    suspend fun getLite(from: String, to: String): List<TxLite>

    @Query("SELECT * FROM transactions WHERE isRemoved = 0 ORDER BY date DESC, created DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT MIN(date) FROM transactions WHERE isRemoved = 0")
    fun observeEarliestDate(): Flow<String?>

    @Query("SELECT MIN(date) FROM transactions WHERE isRemoved = 0")
    suspend fun earliestDate(): String?

    @Query("SELECT * FROM transactions")
    suspend fun getAll(): List<TransactionEntity>

    @Query(
        """SELECT accountUid, SUM(CASE type WHEN 'Income' THEN amountInAccountCurrency
                                            ELSE -amountInAccountCurrency END) AS total
           FROM transactions WHERE isRemoved = 0 GROUP BY accountUid"""
    )
    suspend fun sumByAccount(): List<AccountSum>
}

@Dao
interface TransferDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<TransferEntity>)

    @Upsert
    suspend fun upsert(item: TransferEntity)

    @Query("SELECT * FROM transfer WHERE uid = :uid")
    suspend fun get(uid: String): TransferEntity?

    @Query("SELECT * FROM transfer WHERE isRemoved = 0 AND date BETWEEN :from AND :to ORDER BY date DESC, created DESC")
    fun observeBetween(from: String, to: String): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfer")
    suspend fun getAll(): List<TransferEntity>
}

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<ReminderEntity>)

    @Query("SELECT * FROM reminder WHERE isRemoved = 0 AND kind = :kind ORDER BY startDate")
    fun observeLive(kind: ReminderKind): Flow<List<ReminderEntity>>
}

@Dao
interface RateDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<RateEntity>)

    @Query("SELECT * FROM rate")
    suspend fun getAll(): List<RateEntity>

    @Query("SELECT * FROM rate")
    fun observeAll(): Flow<List<RateEntity>>
}
