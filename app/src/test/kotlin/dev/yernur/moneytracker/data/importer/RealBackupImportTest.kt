package dev.yernur.moneytracker.data.importer

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.summarize
import dev.yernur.moneytracker.data.db.TransactionType
import java.io.File
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.first
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

            // The balance the app will show (same query as the Accounts screen) == the original's cached balance.
            val shown = db.accountDao().observeBalances().first().associate { it.uid to it.balance }
            val cached = snapshot.getJSONObject("balances_cached")
            assertTrue(cached.length() > 0)
            for (uid in cached.keys()) {
                assertEquals("balance of $uid", minor(cached.getDouble(uid)), shown[uid] ?: 0L)
            }

            // The Accounts-screen totals vs the owner's screenshot: Total = Main only, second figure "₸2.35M".
            val rates = db.rateDao().getAll().associate { (it.fromCurrencyCode to it.toCurrencyCode) to it.value }
            val summary = summarize(db.accountDao().getAll().filter { !it.isRemoved }, shown, rates)
            assertEquals(minor(cached.getDouble("main")), summary.counted)
            assertTrue("all accounts ${summary.all}", summary.all in 234_500_000L..235_500_000L)
        } finally {
            db.close()
        }
    }
}
