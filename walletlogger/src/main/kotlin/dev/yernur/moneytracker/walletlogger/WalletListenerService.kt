package dev.yernur.moneytracker.walletlogger

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class WalletListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Recorder(LogStore(this), packageName).onPosted(sbn.packageName) {
            val n = sbn.notification
            val extras = n.extras
            LoggedNotification(
                packageName = sbn.packageName,
                postTime = sbn.postTime,
                notificationId = sbn.id,
                isGroupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
                title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
                text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
                bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            )
        }
    }

    // The OS can drop the binding (battery managers); ask to be bound again.
    override fun onListenerDisconnected() {
        requestRebind(android.content.ComponentName(this, WalletListenerService::class.java))
    }
}
