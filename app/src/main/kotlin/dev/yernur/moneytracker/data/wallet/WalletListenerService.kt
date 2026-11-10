package dev.yernur.moneytracker.data.wallet

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.yernur.moneytracker.MoneyApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Receives every notification once the owner grants notification access, and hands only Wallet's, with consent, to [WalletIntake]. */
class WalletListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val container = (applicationContext as MoneyApp).container
        val pending = WalletIntake(container.settings).accept(sbn.packageName, sbn.postTime) {
            val n = sbn.notification
            WalletRaw(
                n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(), n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
                isGroupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            )
        } ?: return
        scope.launch { WalletPrompts(this@WalletListenerService, container).prompt(pending) }
    }

    // The OS can drop the binding (battery managers); ask to be bound again.
    override fun onListenerDisconnected() {
        requestRebind(ComponentName(this, WalletListenerService::class.java))
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
