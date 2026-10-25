package dev.yernur.moneytracker.data.db

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppDatabaseTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun account(uid: String, currency: String) = AccountEntity(
        uid, uid, currency, null, null, null, null,
        ignoreInBalance = false, isActive = true, isArchived = false, isFavorite = false,
        created = TS, modified = TS,
    )

    private fun category(uid: String) = CategoryEntity(
        uid, uid, TransactionType.Expense, null, null, 0, null, null, null, false, TS, TS,
    )

    private fun tx(uid: String, type: TransactionType, account: String, category: String?, accountAmount: Long?) =
        TransactionEntity(
            uid, type, account, category, accountAmount, null, null, accountAmount, null,
            "2026-10-25", null, TS, TS,
        )

    @Test
    fun sumByAccount_signsByType_andKeepsCents() = runBlocking {
        db.accountDao().insertAll(listOf(account("kzt", "KZT"), account("usd", "USD")))
        db.categoryDao().insertAll(listOf(category("cafe")))
        db.transactionDao().insertAll(
            listOf(
                tx("a", TransactionType.Income, "kzt", "cafe", 1_000_000),
                tx("b", TransactionType.Expense, "kzt", "cafe", 1_353_700),
                tx("c", TransactionType.Expense, "usd", null, 19),
                tx("d", TransactionType.Expense, "usd", null, null), // NULL account amount is ignored by SUM
            )
        )
        val sums = db.transactionDao().sumByAccount().associate { it.accountUid to it.total }
        assertEquals(-353_700L, sums["kzt"])
        assertEquals(-19L, sums["usd"]) // 0.19 stays exact
    }

    @Test
    fun foreignKeys_rejectUnknownAccount() {
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { db.transactionDao().insertAll(listOf(tx("x", TransactionType.Income, "missing", null, 1))) }
        }
    }

    @Test
    fun removedRows_areHiddenFromLiveQueries() = runBlocking {
        db.accountDao().insertAll(listOf(account("kzt", "KZT"), account("gone", "KZT").copy(isRemoved = true)))
        val live = db.accountDao().getAll().filter { !it.isRemoved }
        assertEquals(listOf("kzt"), live.map { it.uid })
    }

    private companion object {
        const val TS = "2026-10-25T00:00:00.000Z"
    }
}
