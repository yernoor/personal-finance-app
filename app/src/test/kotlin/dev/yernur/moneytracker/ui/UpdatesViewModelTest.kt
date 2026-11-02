package dev.yernur.moneytracker.ui

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.AppContainer
import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.sheets.FakeTransport
import dev.yernur.moneytracker.data.sheets.HttpResponse
import dev.yernur.moneytracker.data.sheets.ok
import dev.yernur.moneytracker.data.update.ApkAsset
import dev.yernur.moneytracker.data.update.InstallStatus
import dev.yernur.moneytracker.data.update.UpdateChecker
import dev.yernur.moneytracker.data.update.UpdateInstaller
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class UpdatesViewModelTest {
    private val settings = AppSettings()
    private val newer = """{"tag_name":"v0.3.0","html_url":"https://example/r","assets":[{"name":"a.apk","browser_download_url":"https://example/a.apk","size":10,"digest":null}]}"""

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        InstallStatus.failure.value = null
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakeInstaller(val fail: Exception? = null) : UpdateInstaller {
        val installed = mutableListOf<File>()
        override fun download(apk: ApkAsset, progress: (Float) -> Unit): File {
            progress(0.5f)
            fail?.let { throw it }
            return File("downloaded-${apk.name}")
        }
        override fun install(file: File) { installed += file }
    }

    private fun checker(response: HttpResponse) = UpdateChecker(FakeTransport { response }, "0.2.1")

    /** The work runs on a background thread, so wait for the outcome in real time. */
    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition()) {
            check(System.nanoTime() < deadline) { "condition not met within 5 s" }
            Thread.sleep(10)
        }
    }

    @Test
    fun checkFindsANewerRelease_andRemembersItForTheMoreScreen() {
        val vm = UpdatesViewModel(checker(ok(newer)), FakeInstaller(), settings)
        vm.check()
        waitFor { vm.state is UpdateState.Available }
        assertEquals("v0.3.0", (vm.state as UpdateState.Available).release.tag)
        assertEquals("v0.3.0", settings.availableUpdate.value)
    }

    @Test
    fun checkWithNothingNewClearsTheReminder() {
        settings.setAvailableUpdate("v0.2.5")
        val vm = UpdatesViewModel(checker(ok("""{"tag_name":"v0.2.1","html_url":"u","assets":[]}""")), FakeInstaller(), settings)
        vm.check()
        waitFor { vm.state == UpdateState.UpToDate }
        assertNull(settings.availableUpdate.value)
    }

    @Test
    fun aFailedCheck_saysWhy() {
        val vm = UpdatesViewModel(checker(HttpResponse(403, "{}")), FakeInstaller(), settings)
        vm.check()
        waitFor { vm.state is UpdateState.Failed }
        assertTrue((vm.state as UpdateState.Failed).message.contains("limiting"))
    }

    @Test
    fun installDownloadsThenHandsTheFileToAndroid() {
        val installer = FakeInstaller()
        val vm = UpdatesViewModel(checker(ok(newer)), installer, settings)
        vm.check()
        waitFor { vm.state is UpdateState.Available }
        vm.install((vm.state as UpdateState.Available).release)
        waitFor { vm.state == UpdateState.Installing }
        assertEquals(listOf(File("downloaded-a.apk")), installer.installed)
    }

    @Test
    fun aFailedDownload_isShown_andNothingIsInstalled() {
        val installer = FakeInstaller(fail = IOException("reset"))
        val vm = UpdatesViewModel(checker(ok(newer)), installer, settings)
        vm.check()
        waitFor { vm.state is UpdateState.Available }
        vm.install((vm.state as UpdateState.Available).release)
        waitFor { vm.state is UpdateState.Failed }
        assertEquals("No internet connection.", (vm.state as UpdateState.Failed).message)
        assertTrue(installer.installed.isEmpty())
    }

    @Test
    fun aReleaseWithoutAnApk_cannotBeInstalled() {
        val body = """{"tag_name":"v0.3.0","html_url":"u","assets":[]}"""
        val vm = UpdatesViewModel(checker(ok(body)), FakeInstaller(), settings)
        vm.check()
        waitFor { vm.state is UpdateState.Available }
        vm.install((vm.state as UpdateState.Available).release)
        assertTrue((vm.state as UpdateState.Failed).message.contains("no APK"))
    }

    @Test
    fun aFailureReportedByAndroidLater_isShown() {
        val vm = UpdatesViewModel(checker(ok(newer)), FakeInstaller(), settings)
        InstallStatus.failure.value = "INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match"
        waitFor { vm.state is UpdateState.Failed }
        assertTrue((vm.state as UpdateState.Failed).message.contains("signatures"))
        assertNull(InstallStatus.failure.value)
    }

    @Test
    fun theDailyCheck_asksOnceADay_andStaysSilentOnFailure() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        val transport = FakeTransport { ok(newer) }
        val container = AppContainer(db, settings, updateChecker = UpdateChecker(transport, "0.2.1"), installedVersion = "0.2.1")
        val day = 24 * 60 * 60 * 1000L

        container.checkForUpdateDaily(now = 10 * day)
        assertEquals(1, transport.requests.size)
        assertEquals("v0.3.0", settings.availableUpdate.value)

        container.checkForUpdateDaily(now = 10 * day + 3_600_000) // an hour later: no second request
        assertEquals(1, transport.requests.size)
        container.checkForUpdateDaily(now = 11 * day + 1) // a day later
        assertEquals(2, transport.requests.size)

        val broken = AppContainer(db, AppSettings(), updateChecker = UpdateChecker(FakeTransport { HttpResponse(500, "") }, "0.2.1"))
        broken.checkForUpdateDaily(now = 20 * day) // must not throw
        db.close()
    }
}
