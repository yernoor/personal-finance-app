package dev.yernur.moneytracker.walletlogger

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** A silent, ongoing notification shown only while the logger can read (access granted) and reading is not paused. */
object StatusNotification {
    private const val CHANNEL = "status"
    private const val ID = 1

    fun shouldShow(accessGranted: Boolean, readingEnabled: Boolean) = accessGranted && readingEnabled

    fun update(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val access = manager.isNotificationListenerAccessGranted(ComponentName(context, WalletListenerService::class.java))
        val store = LogStore(context)
        if (!shouldShow(access, store.readingEnabled())) {
            manager.cancel(ID)
            return
        }
        // IMPORTANCE_LOW: no sound, no heads-up.
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Logger status", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Wallet Logger is reading")
            .setContentText(store.allowedPackages().joinToString())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
        manager.notify(ID, notification)
    }
}
