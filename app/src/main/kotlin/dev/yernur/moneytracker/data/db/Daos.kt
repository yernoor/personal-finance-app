package dev.yernur.moneytracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Net movement of one account in account-currency minor units (transactions only, transfers excluded). */
data class AccountSum(val accountUid: String, val total: Long)

@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<AccountEntity>)

    @Query("SELECT * FROM account WHERE isRemoved = 0 ORDER BY position")
    fun observeLive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM account")
    suspend fun getAll(): List<AccountEntity>
}

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CategoryEntity>)

    @Query("SELECT * FROM category WHERE isRemoved = 0 ORDER BY position")
    fun observeLive(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category")
    suspend fun getAll(): List<CategoryEntity>
}

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TagEntity>)

    @Query("SELECT * FROM tag WHERE isRemoved = 0 ORDER BY name")
    fun observeLive(): Flow<List<TagEntity>>
}

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TransactionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTagLinks(items: List<TransactionTagEntity>)

    @Query("SELECT * FROM transactions WHERE isRemoved = 0 AND date BETWEEN :from AND :to ORDER BY date DESC, created DESC")
    fun observeBetween(from: String, to: String): Flow<List<TransactionEntity>>

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
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TransferEntity>)

    @Query("SELECT * FROM transfer WHERE isRemoved = 0 AND date BETWEEN :from AND :to ORDER BY date DESC, created DESC")
    fun observeBetween(from: String, to: String): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfer")
    suspend fun getAll(): List<TransferEntity>
}

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ReminderEntity>)

    @Query("SELECT * FROM reminder WHERE isRemoved = 0 AND kind = :kind ORDER BY startDate")
    fun observeLive(kind: ReminderKind): Flow<List<ReminderEntity>>
}

@Dao
interface RateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RateEntity>)

    @Query("SELECT * FROM rate")
    suspend fun getAll(): List<RateEntity>
}
