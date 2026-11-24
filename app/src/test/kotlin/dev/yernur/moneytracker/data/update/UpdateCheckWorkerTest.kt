package dev.yernur.moneytracker.data.update

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.ui.UPDATES_LAUNCH
import dev.yernur.moneytracker.widget.EXTRA_KIND
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class UpdateCheckWorkerTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val settings = AppSettings()

    @Test
    fun announcesEachVersionOnce_andOpensAppUpdates() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        UpdateCheckWorker.announce(app, settings, "v0.9.0")
        val posted = shadowOf(manager).allNotifications.single()
        assertEquals("Version 0.9.0 is available", shadowOf(posted).contentTitle)
        assertEquals(UPDATES_LAUNCH, shadowOf(posted.contentIntent).savedIntent.getStringExtra(EXTRA_KIND))
        assertEquals("v0.9.0", settings.notifiedUpdate.value)

        manager.cancelAll()
        UpdateCheckWorker.announce(app, settings, "v0.9.0") // the same version on the next day: quiet
        assertEquals(0, shadowOf(manager).allNotifications.size)
        UpdateCheckWorker.announce(app, settings, "v0.9.1")
        assertEquals(1, shadowOf(manager).allNotifications.size)
    }

    @Test
    fun announcesOnlyAVersionNewerThanTheInstalledApp() {
        assertEquals("v0.9.0", UpdateCheckWorker.toAnnounce("v0.9.0", "0.8.2")) // found by this job or by the More screen earlier
        assertEquals(null, UpdateCheckWorker.toAnnounce("v0.9.0", "0.9.0")) // installed since the last check
        assertEquals(null, UpdateCheckWorker.toAnnounce(null, "0.8.2"))
    }

    @Test
    fun withoutPermission_nothingIsPosted_andTheVersionIsNotMarked() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        UpdateCheckWorker.announce(app, settings, "v0.9.0")
        assertEquals(0, shadowOf(manager).allNotifications.size)
        assertEquals(null, settings.notifiedUpdate.value)
    }
}
