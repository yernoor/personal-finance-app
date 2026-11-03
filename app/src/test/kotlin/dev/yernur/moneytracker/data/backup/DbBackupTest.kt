package dev.yernur.moneytracker.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.data.db.AccountEntity
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.DB_VERSION
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class DbBackupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var backup: DbBackup
    private val dbFile get() = context.getDatabasePath("backup-test.db")

    @Before
    fun setUp() {
        context.deleteDatabase("backup-test.db")
        db = Room.databaseBuilder(context, AppDatabase::class.java, "backup-test.db").allowMainThreadQueries().build()
        backup = DbBackup(db, dbFile, File(context.filesDir, "backup-test"), DB_VERSION)
    }

    private fun account(uid: String) = AccountEntity(uid, uid, "KZT", null, null, null, null, false, true, false, false, "", "")

    private fun accountCount(d: AppDatabase) = runBlocking { d.accountDao().observeBalances().first().size }

    @Test
    fun roundTrip_restoreBringsBackWhatWasBackedUp_andKeepsTheOldFileAsBak() = runBlocking {
        db.accountDao().insertAll(listOf(account("a"), account("b")))
        val file = File(context.cacheDir, "out.db")
        file.outputStream().use { backup.write(it) }

        db.accountDao().insertAll(listOf(account("c")))
        assertEquals(3, accountCount(db))

        val (staged, info) = backup.stage(file.inputStream())
        assertEquals(2, info.accounts)
        backup.restore(staged)
        assertTrue(File(dbFile.path + ".bak").exists())

        val reopened = Room.databaseBuilder(context, AppDatabase::class.java, "backup-test.db").allowMainThreadQueries().build()
        assertEquals(2, accountCount(reopened))
        reopened.close()
    }

    @Test
    fun refusesAFileThatIsNotSqlite() {
        val junk = "not a database at all".byteInputStream()
        try {
            backup.stage(junk)
            fail("should refuse")
        } catch (e: BackupException) {
            assertTrue(e.message!!.contains("not a Money Tracker backup"))
        }
    }

    @Test
    fun refusesABackupFromANewerSchema() {
        val file = File(context.cacheDir, "newer.db")
        file.outputStream().use { backup.write(it) }
        val older = DbBackup(db, dbFile, File(context.filesDir, "backup-test"), DB_VERSION - 1)
        try {
            older.inspect(file)
            fail("should refuse")
        } catch (e: BackupException) {
            assertTrue(e.message!!.contains("newer version"))
        }
    }

    @Test
    fun autoBackup_isWrittenAndFoundAgain() {
        val written = backup.writeAutoBackup()
        assertEquals(written, backup.autoBackup())
        assertEquals(DB_VERSION, backup.inspect(written).schemaVersion)
    }
}
