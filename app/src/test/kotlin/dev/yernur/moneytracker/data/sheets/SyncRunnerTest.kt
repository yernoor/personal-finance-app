package dev.yernur.moneytracker.data.sheets

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.LedgerRepository
import dev.yernur.moneytracker.data.TransactionDraft
import dev.yernur.moneytracker.data.db.AccountEntity
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.CategoryEntity
import dev.yernur.moneytracker.data.db.TransactionType
import dev.yernur.moneytracker.data.google.AuthResult
import dev.yernur.moneytracker.data.google.Authorizer
import android.content.Intent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncRunnerTest {
    private lateinit var db: AppDatabase
    private val settings = AppSettings().apply { setSpreadsheet("sid", "https://docs.google.com/spreadsheets/d/sid/edit") }

    private val authorizer = object : Authorizer {
        override suspend fun authorize() = AuthResult("token", null)
        override fun resultFrom(data: Intent?) = AuthResult("token", null)
        override suspend fun freshToken(rejected: String) = "token"
    }

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        db.accountDao().insertAll(listOf(AccountEntity("main", "Main", "KZT", null, null, 0, null, false, true, false, false, "", "")))
        db.categoryDao().insertAll(listOf(CategoryEntity("cafe", "Кафе", TransactionType.Expense, null, null, 0, null, null, 0, false, "", "")))
        LedgerRepository(db).saveTransaction(TransactionDraft(null, TransactionType.Expense, "main", "cafe", 100_000, "2026-10-21", "c", emptySet()))
    }

    @After
    fun tearDown() = db.close()

    /** An existing spreadsheet with every tab, and an empty sheet when read. */
    private val google = FakeTransport { request ->
        when {
            request.url.startsWith(SheetsApi.DRIVE_URL) -> ok("""{"files":[]}""")
            request.method == "GET" && request.url.contains("batchGet") -> ok("""{"valueRanges":[]}""")
            request.method == "GET" -> ok(
                """{"spreadsheetUrl":"https://docs.google.com/spreadsheets/d/sid/edit","sheets":[""" +
                    SheetLayout.ALL.mapIndexed { i, t -> """{"properties":{"sheetId":${i * 10},"title":"${t.title}","gridProperties":{"rowCount":5}}}""" }.joinToString(",") + "]}",
            )
            else -> ok()
        }
    }

    private fun pulls() = google.requests.count { it.url.contains("batchGet") }

    @Test
    fun theFirstSync_onlyPushes_soAnOldSheetCannotOverwritePhoneEdits() = runBlocking {
        val runner = SyncRunner(db, settings, google)
        assertNull(settings.lastSyncAt.value)

        assertNull(runner.run(authorizer)) // first: push only
        assertEquals(0, pulls())
        assertNotNull(settings.lastSyncAt.value) // the push recorded the sync time

        assertNotNull(runner.run(authorizer)) // after that it pulls too
        assertEquals(1, pulls())
        assertTrue(google.requests.any { it.url.endsWith("values:batchUpdate") })
    }
}
