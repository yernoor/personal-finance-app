package dev.yernur.moneytracker.walletstub

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.math.BigDecimal
import java.util.Locale

/** The id the real Wallet reused for both payments seen on the owner's phone (docs/FEATURES.md), so repeats replace each other. */
private const val NOTIFICATION_ID = 1001
private const val CHANNEL_ID = "payments"

/** What the real Wallet shows as the notification text: `KZT1,234.00 with <card> ••<last4>` (verified on the owner's phone). */
internal fun paymentText(currency: String, amount: String, card: String, last4: String): String =
    "$currency${"%,.2f".format(Locale.US, BigDecimal(amount))} with $card ••$last4"

/**
 * Posts one Wallet-style payment notification. From a shell:
 * `adb shell am broadcast -a dev.yernur.moneytracker.walletstub.POST -n com.google.android.apps.walletnfcrel/dev.yernur.moneytracker.walletstub.PostReceiver --es merchant "Test Cafe" --es amount 1234.5`
 * Optional extras: `currency` (KZT), `card` (Visa), `last4` (1234), `text` (replaces the whole text line, for formats not seen yet: declines, refunds, foreign currency).
 */
class PostReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val merchant = intent.getStringExtra("merchant") ?: "Test Cafe"
        val text = intent.getStringExtra("text") ?: paymentText(
            intent.getStringExtra("currency") ?: "KZT", intent.getStringExtra("amount") ?: "1234.00",
            intent.getStringExtra("card") ?: "Visa", intent.getStringExtra("last4") ?: "1234",
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Payments", NotificationManager.IMPORTANCE_DEFAULT))
        manager.notify(
            NOTIFICATION_ID,
            Notification.Builder(context, CHANNEL_ID).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(merchant).setContentText(text).build(),
        )
    }
}
