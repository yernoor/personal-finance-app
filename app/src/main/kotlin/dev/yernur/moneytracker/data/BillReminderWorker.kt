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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Bill reminders (Recurring costs, More -> Notifications): when one is due it posts "Internet is due tomorrow" with the amount, then books
 * itself for the next one. Like [ReminderWorker], a one-time job (periodic work cannot be pinned to a clock time); Android may run it a few
 * minutes late (Doze). Ends that have passed are settled first, so an ended bill never reminds.
 */
class BillReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val bills = (applicationContext as MoneyApp).container.bills
        val now = LocalDateTime.now()
        bills.settleEnded(now.toLocalDate())
        val list = bills.current(now.toLocalDate())
        dueNow(list, now).forEach { bill ->
            post(applicationContext, bill, now.toLocalDate())
            bills.markNotified(bill.key, bill.reminderAt!!.toLocalDate())
        }
        next(list, now)?.let { schedule(applicationContext, it) } // the running job is this one: REPLACE books the next
        return Result.success()
    }

    private fun post(context: Context, bill: Bill, today: LocalDate) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Bill reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        manager.notify(
            bill.key.hashCode(),
            Notification.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(dueTitle(bill, today)).setContentText(Money.formatRounded(bill.item.amount, DEFAULT_CURRENCY))
                .setContentIntent(open).setAutoCancel(true).build(),
        )
    }

    companion object {
        private const val NAME = "bill-reminders"
        private const val CHANNEL = "bill_reminders"
        /** A reminder found later than this (phone off) is skipped rather than posted late. */
        private val LATE_LIMIT = Duration.ofHours(12)

        /** Reminders whose time has come (not more than [LATE_LIMIT] ago) and that have not been posted for this due date. */
        fun dueNow(bills: List<Bill>, now: LocalDateTime): List<Bill> = bills.filter { bill ->
            val at = bill.reminderAt ?: return@filter false
            !at.isAfter(now) && Duration.between(at, now) <= LATE_LIMIT && bill.settings?.lastNotified != at.toLocalDate().toString()
        }

        /** The next reminder time after [now], or null when there is none. */
        fun next(bills: List<Bill>, now: LocalDateTime): LocalDateTime? = bills.mapNotNull { it.reminderAt }.filter { it.isAfter(now) }.minOrNull()

        /** "Internet is due today", "... tomorrow", "... on Dec 5". */
        fun dueTitle(bill: Bill, today: LocalDate): String {
            val due = bill.item.next
            val day = when (due) {
                today -> "today"
                today.plusDays(1) -> "tomorrow"
                else -> "on " + due.format(DateTimeFormatter.ofPattern("MMM d"))
            }
            return "${bill.item.title} is due $day"
        }

        /**
         * Books a run at [at]; without it, a run now, which works out what is due and books the next one itself (after any change to a bill,
         * and on app start). Best effort: a missing WorkManager must never stop the app (unit tests have none).
         */
        fun schedule(context: Context, at: LocalDateTime? = null) {
            runCatching {
                val delay = at?.let { Duration.between(LocalDateTime.now(), it).toMillis().coerceAtLeast(0) } ?: 0L
                WorkManager.getInstance(context).enqueueUniqueWork(
                    NAME, ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<BillReminderWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build(),
                )
            }
        }
    }
}
