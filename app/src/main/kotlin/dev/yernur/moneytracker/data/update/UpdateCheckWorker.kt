package dev.yernur.moneytracker.data.update

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.yernur.moneytracker.MainActivity
import dev.yernur.moneytracker.MoneyApp
import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.ui.UPDATES_LAUNCH
import dev.yernur.moneytracker.widget.EXTRA_KIND
import java.util.concurrent.TimeUnit

/** Once a day (online), asks GitHub for a newer release and, the first time a version shows up, says so in a notification that opens App updates. */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as MoneyApp).container
        // 20 h, not 24: a job landing just after the More screen's own check would otherwise skip a whole day.
        val found = container.checkForUpdateDaily(minGapMillis = TimeUnit.HOURS.toMillis(20))
        if (found != null) announce(applicationContext, container.settings, found.tag)
        return Result.success()
    }

    companion object {
        private const val NAME = "daily-update-check"
        private const val CHANNEL = "update_available" // not "app_update": that one is the quiet download channel, and importance cannot be raised later
        private const val NOTIFICATION_ID = 7003

        /** Posts "Version X is available" once per [tag]; nothing without the notification permission (it is then tried again for the next check). */
        fun announce(context: Context, settings: AppSettings, tag: String) {
            if (tag == settings.notifiedUpdate.value) return
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "New versions", NotificationManager.IMPORTANCE_DEFAULT))
            val intent = Intent(context, MainActivity::class.java).putExtra(EXTRA_KIND, UPDATES_LAUNCH)
            // Own request code: the reminders use 0 with the same component, and FLAG_UPDATE_CURRENT would swap their extras.
            val open = PendingIntent.getActivity(context, NOTIFICATION_ID, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            manager.notify(
                NOTIFICATION_ID,
                Notification.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("Version ${tag.removePrefix("v")} is available").setContentText("Tap to see what's new and install it.")
                    .setContentIntent(open).setAutoCancel(true).build(),
            )
            settings.setNotifiedUpdate(tag)
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            runCatching { WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request) } // no WorkManager in unit tests
        }
    }
}
