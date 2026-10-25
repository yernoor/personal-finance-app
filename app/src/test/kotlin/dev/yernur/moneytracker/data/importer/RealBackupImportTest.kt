package dev.yernur.moneytracker.data.importer

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.TransactionType
import java.io.File
import kotlin.math.roundToLong
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Imports the owner's newest real backup and compares with data/extracted/snapshot.json (written by
 * tools/mmbackup.py, i.e. computed independently of the Kotlin code under test). Skipped when data/ is absent.
 * Re-run `tools/mmbackup.py extract` after dropping a newer backup, or the snapshot will not match.
 */
@RunWith(RobolectricTestRunner::class)
class RealBackupImportTest {
    private val dataDir = File("../data")

    private fun minor(units: Double) = (units * 100).roundToLong()

    @Test
    fun importMatchesSnapshotToTheCent() = runBlocking {
        val backup = dataDir.listFiles { f -> f.name.endsWith(".mmbackup") }?.maxByOrNull { it.name }
        val snapshotFile = File(dataDir, "extracted/snapshot.json")
        assumeTrue("no backup or snapshot in data/", backup != null && snapshotFile.exists())
        val snapshot = JSONObject(snapshotFile.readText())

        val tempDir = File(System.getProperty("java.io.tmpdir"))
        val result = MmbackupMapper.map(MmbackupReader.read(backup!!.readBytes(), tempDir))
        assertEquals("importer warnings", emptyList<String>(), result.warnings)
        assertEquals(0, result.unresolvedAccountAmount)

        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            ImportRepository(db).store(result)

            val counts = snapshot.getJSONObject("counts")
            val transactions = db.transactionDao().getAll()
            val transfers = db.transferDao().getAll()
            assertEquals(counts.getInt("transactions"), transactions.size)
            assertEquals(counts.getInt("transfers"), transfers.size)
            assertEquals(counts.getInt("accounts"), db.accountDao().getAll().size)
            assertEquals(counts.getInt("categories"), db.categoryDao().getAll().size)

            val totals = snapshot.getJSONObject("totals_default_currency")
            fun total(type: TransactionType) = transactions.filter { it.type == type }.sumOf { it.amountInDefaultCurrency ?: 0L }
            assertEquals(minor(totals.getDouble("income")), total(TransactionType.Income))
            assertEquals(minor(totals.getDouble("expense")), total(TransactionType.Expense))

            // Per-account balance from imported rows (transfers fall back to fromAmount) == the app's cached balance.
            val fromTransactions = db.transactionDao().sumByAccount().associate { it.accountUid to it.total }
            val cached = snapshot.getJSONObject("balances_cached")
            assertTrue(cached.length() > 0)
            for (uid in cached.keys()) {
                val incoming = transfers.filter { it.toAccountUid == uid }.sumOf { it.toAmount ?: it.fromAmount }
                val outgoing = transfers.filter { it.fromAccountUid == uid }.sumOf { it.fromAmount }
                val balance = (fromTransactions[uid] ?: 0L) + incoming - outgoing
                assertEquals("balance of $uid", minor(cached.getDouble(uid)), balance)
            }
        } finally {
            db.close()
        }
    }
}
