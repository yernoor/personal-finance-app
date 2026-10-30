package dev.yernur.moneytracker.ui

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.db.AccountEntity
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.google.AuthResult
import dev.yernur.moneytracker.data.google.Authorizer
import dev.yernur.moneytracker.data.sheets.FakeTransport
import dev.yernur.moneytracker.data.sheets.HttpResponse
import dev.yernur.moneytracker.data.sheets.SheetsException
import dev.yernur.moneytracker.data.sheets.ok
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SyncViewModelTest {
    private lateinit var db: AppDatabase
    private val settings = AppSettings()

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        db.accountDao().insertAll(listOf(AccountEntity("main", "Main", "KZT", null, null, 0, null, false, true, false, false, "", "")))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private class FakeAuthorizer(
        private val first: () -> AuthResult,
        private val afterConsent: AuthResult = AuthResult("granted-token", null),
    ) : Authorizer {
        val refreshed = mutableListOf<String>()
        override suspend fun authorize() = first()
        override fun resultFrom(data: Intent?) = afterConsent
        override suspend fun freshToken(rejected: String): String = "fresh".also { refreshed += rejected }
    }

    private val googleOk = FakeTransport { request ->
        if (request.url.endsWith("/spreadsheets")) {
            val titles = JSONObject(request.body!!).getJSONArray("sheets").let { s -> (0 until s.length()).map { s.getJSONObject(it).getJSONObject("properties").getString("title") } }
            val sheets = titles.mapIndexed { i, t -> """{"properties":{"sheetId":$i,"title":"$t"}}""" }.joinToString(",")
            ok("""{"spreadsheetId":"sid","spreadsheetUrl":"https://docs.google.com/spreadsheets/d/sid/edit","sheets":[$sheets]}""")
        } else ok()
    }

    /** The push runs on a background thread, so wait for its outcome in real time. */
    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition()) {
            check(System.nanoTime() < deadline) { "condition not met within 5 s" }
            Thread.sleep(10)
        }
    }

    @Test
    fun connect_onlyRecordsTheGrant_andCreatesNothing() {
        val vm = SyncViewModel(db, settings, googleOk)
        vm.connect(FakeAuthorizer({ AuthResult("token", null) }))
        waitFor { settings.googleConnected.value }
        waitFor { vm.state == SyncState.Idle }
        assertNull(settings.spreadsheetId.value)
        assertTrue(googleOk.requests.isEmpty())
    }

    @Test
    fun connect_needsConsent_pausesForTheConsentScreen_thenRecordsTheGrant() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val consent = PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE).intentSender
        val vm = SyncViewModel(db, settings, googleOk)
        val authorizer = FakeAuthorizer({ AuthResult(null, consent) })
        vm.connect(authorizer)
        waitFor { vm.state is SyncState.NeedsConsent }
        assertFalse(settings.googleConnected.value)

        vm.onConsentResult(authorizer, Intent())
        waitFor { settings.googleConnected.value }
        assertNull(settings.spreadsheetId.value) // still no sheet: that is step 2
        assertTrue(googleOk.requests.isEmpty())
    }

    @Test
    fun createSheet_createsAndUploads_usingTheTokenGoogleGaveForThatCall() {
        settings.setGoogleConnected(true)
        val vm = SyncViewModel(db, settings, googleOk)
        vm.createSheet(FakeAuthorizer({ AuthResult("token", null) }))
        waitFor { settings.spreadsheetId.value != null }
        assertEquals("sid", settings.spreadsheetId.value)
        assertTrue(googleOk.requests.all { it.headers["Authorization"] == "Bearer token" })
        waitFor { vm.state == SyncState.Idle }
    }

    @Test
    fun createSheet_afterTheAccessWasRevoked_asksToConnectAgain_andCreatesNothing() {
        settings.setGoogleConnected(true)
        val context = ApplicationProvider.getApplicationContext<Application>()
        val consent = PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE).intentSender
        val vm = SyncViewModel(db, settings, googleOk)
        vm.createSheet(FakeAuthorizer({ AuthResult(null, consent) }))
        waitFor { vm.state is SyncState.Failed }
        assertFalse(settings.googleConnected.value)
        assertTrue((vm.state as SyncState.Failed).message.contains("Connect Google"))
        assertTrue(googleOk.requests.isEmpty())
    }

    @Test
    fun aRejectedTokenIsReplacedOnce_throughTheAuthorizer() {
        var calls = 0
        val transport = FakeTransport { request ->
            if (calls++ == 0) HttpResponse(401, "{}") else googleOk.execute(request)
        }
        val authorizer = FakeAuthorizer({ AuthResult("stale", null) })
        SyncViewModel(db, settings, transport).createSheet(authorizer)
        waitFor { settings.spreadsheetId.value != null }
        assertEquals(listOf("stale"), authorizer.refreshed)
        assertEquals("Bearer fresh", transport.requests[1].headers["Authorization"])
    }

    @Test
    fun aMissingOAuthClient_isExplainedInPlainWords() {
        val vm = SyncViewModel(db, settings, googleOk)
        vm.connect(FakeAuthorizer({ throw ApiException(Status(10)) }))
        waitFor { vm.state is SyncState.Failed }
        val message = (vm.state as SyncState.Failed).message
        assertTrue(message, message.contains("docs/GOOGLE_SETUP.md"))
        assertNull(settings.spreadsheetId.value)
        assertTrue(googleOk.requests.isEmpty())
    }

    @Test
    fun forgettingTheSheet_clearsTheLink() {
        settings.setSpreadsheet("sid", "https://example/sid")
        val vm = SyncViewModel(db, settings, googleOk)
        vm.forget()
        assertNull(settings.spreadsheetId.value)
        assertEquals(SyncState.Idle, vm.state)
    }

    @Test
    fun errorsAreDescribedForTheOwner() {
        assertEquals("Sign-in was cancelled.", describeError(ApiException(Status(12501))))
        assertTrue(describeError(ApiException(Status(7))).startsWith("No connection"))
        assertTrue(describeError(SheetsException(403, "Sheets API has not been used in project")).contains("Google Sheets API is enabled"))
        assertEquals("No internet connection.", describeError(IOException("unreachable")))
        assertNotNull(describeError(IllegalStateException()))
    }
}
