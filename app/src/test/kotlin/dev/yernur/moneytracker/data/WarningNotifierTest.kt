package dev.yernur.moneytracker.data

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.data.db.AccountEntity
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.CategoryEntity
import dev.yernur.moneytracker.data.db.TransactionType
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class WarningNotifierTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: AppDatabase
    private lateinit var settings: AppSettings
    private lateinit var ledger: LedgerRepository
    private lateinit var notifier: WarningNotifier
    private val today = LocalDate.of(2026, 10, 10)

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        settings = AppSettings()
        ledger = LedgerRepository(db)
        notifier = WarningNotifier(app, db, settings, ForecastRepository(db, settings) { today })
        db.accountDao().insertAll(listOf(AccountEntity("main", "Main", "KZT", null, null, 0, null, false, true, false, false, "", "")))
        // "Досуг" with a 50,000 KZT monthly limit
        db.categoryDao().insertAll(listOf(CategoryEntity("fun", "Досуг", TransactionType.Expense, null, null, 0, null, 5_000_000, 0, false, "", "")))
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun tearDown() = db.close()

    private fun spend(amount: Long, date: String = "2026-10-05") = runBlocking {
        ledger.saveTransaction(TransactionDraft(null, TransactionType.Expense, "main", "fun", amount, date, null, emptySet()))
    }

    private fun posted() = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications

    @Test
    fun projectedOverrun_notifiesOnce_thenEscalatesToExceeded() = runBlocking {
        spend(4_000_000) // 40,000 KZT by day 10 -> on track for 124,000
        notifier.checkAndNotify()
        notifier.checkAndNotify() // same level again: no duplicate
        assertEquals(1, posted().size)
        assertEquals("Досуг", posted().single().extras.getString("android.title"))
        assertTrue(posted().single().extras.getString("android.text")!!.startsWith("On track for ₸124,000"))

        spend(1_500_000) // now 55,000 spent: over the limit
        notifier.checkAndNotify()
        assertEquals(2, posted().size)
        assertTrue(posted().last().extras.getString("android.text")!!.startsWith("Spent ₸55,000"))
    }

    @Test
    fun noNotificationWithoutPermission_orWhenDisabled_orUnderTheLimit() = runBlocking {
        spend(100_000) // 1,000 KZT: far below
        notifier.checkAndNotify()
        assertEquals(0, posted().size)

        spend(4_000_000)
        settings.setNotifications(false)
        notifier.checkAndNotify()
        assertEquals(0, posted().size)

        settings.setNotifications(true)
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifier.checkAndNotify()
        assertEquals(0, posted().size)

        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS) // not marked as warned while blocked
        notifier.checkAndNotify()
        assertEquals(1, posted().size)
    }
}
