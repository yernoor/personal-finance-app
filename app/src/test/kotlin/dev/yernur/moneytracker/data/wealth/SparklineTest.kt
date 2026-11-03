package dev.yernur.moneytracker.data.wealth

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.AppContainer
import dev.yernur.moneytracker.data.db.AccountEntity
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.sheets.FakeTransport
import dev.yernur.moneytracker.data.sheets.ok
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

class SparklineTest {
    @Test
    fun x_followsTheDate_soAGapLooksLikeAGap() {
        val points = sparklinePoints(listOf(LocalDate.of(2026, 1, 1) to 100L, LocalDate.of(2026, 1, 2) to 150L, LocalDate.of(2026, 1, 22) to 200L))!!
        assertEquals(listOf(0f, 1f / 21, 1f), points.map { it.first })
        assertEquals(listOf(0f, 0.5f, 1f), points.map { it.second })
    }

    @Test
    fun aFlatHistoryIsDrawnInTheMiddle_andOnePointIsNoLine() {
        assertEquals(listOf(0.5f, 0.5f), sparklinePoints(listOf(LocalDate.of(2026, 1, 1) to 5L, LocalDate.of(2026, 1, 2) to 5L))!!.map { it.second })
        assertNull(sparklinePoints(listOf(LocalDate.of(2026, 1, 1) to 5L)))
        assertNull(sparklinePoints(emptyList()))
    }
}

@RunWith(RobolectricTestRunner::class)
class DailyRefreshTest {
    @Test
    fun onStart_ratesAreFetchedFirst_thenTodaysSnapshotIsSaved() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        val feed = "<rates><item><title>USD</title><description>450</description><quant>1</quant></item></rates>"
        val transport = FakeTransport { ok(feed) }
        db.accountDao().insertAll(listOf(AccountEntity("usd", "usd", "USD", null, null, null, null, false, true, false, false, "", "")))
        val container = AppContainer(db, fxClient = NbkRatesClient(transport))

        container.dailyWealthRefresh()

        // The dollar account has a rate by the time the snapshot is taken, so the snapshot is not skipped.
        assertEquals(1, transport.requests.size)
        assertEquals(listOf(LocalDate.now().toString()), db.netWorthSnapshotDao().getAll().map { it.date })
        db.close()
    }
}
