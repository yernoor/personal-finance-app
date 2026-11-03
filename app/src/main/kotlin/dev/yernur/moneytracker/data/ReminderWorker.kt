package dev.yernur.moneytracker.data

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.yernur.moneytracker.MainActivity
import dev.yernur.moneytracker.MoneyApp
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * "Add your expenses" reminder at the configured time of day. A one-time job that books the next one when it runs, because periodic work
 * cannot be pinned to a clock time. Android may delay it a few minutes (Doze); that is accepted for a reminder.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = (applicationContext as MoneyApp).container.settings
        if (!settings.reminderEnabled.value) return Result.success()
        post(applicationContext)
        schedule(applicationContext, settings, replace = true) // the running job is this one: KEEP would keep it and book nothing
        return Result.success()
    }

    private fun post(context: Context) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        manager.notify(
            NOTIFICATION_ID,
            Notification.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentTitle("Add today's expenses").setContentText("Take a minute to log what you spent today.")
                .setContentIntent(open).setAutoCancel(true).build(),
        )
    }

    companion object {
        private const val NAME = "daily-reminder"
        private const val CHANNEL = "daily_reminder"
        private const val NOTIFICATION_ID = 7001

        /** Milliseconds from [now] to the next [minuteOfDay]; a time that is already past today means tomorrow. */
        fun delayMillis(now: LocalDateTime, minuteOfDay: Int): Long {
            var next = now.toLocalDate().atStartOfDay().plusMinutes(minuteOfDay.toLong())
            if (!next.isAfter(now)) next = next.plusDays(1)
            return Duration.between(now, next).toMillis()
        }

        /** Books (or, when switched off, cancels) the next reminder. Best effort: a missing WorkManager must never stop the app (unit tests have none). */
        fun schedule(context: Context, settings: AppSettings, replace: Boolean = false) {
            runCatching {
                val work = WorkManager.getInstance(context)
                if (!settings.reminderEnabled.value) { work.cancelUniqueWork(NAME); return }
                val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(delayMillis(LocalDateTime.now(), settings.reminderMinute.value), TimeUnit.MILLISECONDS).build()
                work.enqueueUniqueWork(NAME, if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
            }
        }
    }
}
