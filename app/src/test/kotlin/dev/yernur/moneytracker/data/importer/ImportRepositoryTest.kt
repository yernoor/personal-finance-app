package dev.yernur.moneytracker.data.importer

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.data.db.AccountEntity
import dev.yernur.moneytracker.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImportRepositoryTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun account(title: String, modified: String) = AccountEntity("main", title, "KZT", null, null, 0, null, false, true, false, false, "", modified)

    private fun result(vararg accounts: AccountEntity) = ImportResult(accounts.toList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), 0, 0)

    @Test
    fun aRowOnThePhone_isReplacedOnlyByANewerBackupCopy() = runBlocking {
        val repo = ImportRepository(db)
        repo.store(result(account("Phone edit", "2026-11-02T10:00:00.000Z")))

        repo.store(result(account("Old backup", "2026-10-01T10:00:00.000Z")))
        assertEquals("Phone edit", db.accountDao().get("main")!!.title) // older backup: the phone keeps its edit

        repo.store(result(account("New backup", "2026-11-03T10:00:00.000Z")))
        assertEquals("New backup", db.accountDao().get("main")!!.title)
    }
}
