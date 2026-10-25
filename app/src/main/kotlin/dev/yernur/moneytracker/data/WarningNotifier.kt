package dev.yernur.moneytracker.data

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import dev.yernur.moneytracker.data.db.AppDatabase

/** Posts a notification the first time a category's spending crosses a warning level this month. */
class WarningNotifier(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: AppSettings,
    private val forecasts: ForecastRepository,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    suspend fun checkAndNotify() {
        if (!settings.notifications.value) return
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val forecast = forecasts.current()
        val titles = db.categoryDao().getAll().associate { it.uid to it.title }
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Spending warnings", NotificationManager.IMPORTANCE_DEFAULT))
        for (category in forecast.warnings) {
            val kind = category.warnings.min() // WarningKind is ordered most urgent first
            val key = "${forecast.month}-${category.categoryUid}-$kind"
            if (settings.isWarned(key)) continue
            val name = titles[category.categoryUid] ?: "No category"
            val notification = Notification.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(name)
                .setContentText(message(kind, category))
                .build()
            manager.notify(key.hashCode(), notification)
            settings.markWarned(key)
        }
    }

    private fun message(kind: WarningKind, c: CategoryForecast): String {
        val limit = Money.format(c.limit ?: 0, DEFAULT_CURRENCY)
        return when (kind) {
            WarningKind.Exceeded -> "Spent ${Money.format(c.spent, DEFAULT_CURRENCY)}, over the $limit limit"
            WarningKind.RunRate -> "On track for ${Money.format(c.runRate, DEFAULT_CURRENCY)} this month (limit $limit)"
            WarningKind.Average -> "Usually ${Money.format(c.average ?: 0, DEFAULT_CURRENCY)} a month (limit $limit)"
        }
    }

    private companion object {
        const val CHANNEL = "limit_warnings"
    }
}
