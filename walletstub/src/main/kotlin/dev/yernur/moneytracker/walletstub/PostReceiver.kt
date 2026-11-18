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

/** What the real Wallet shows as the notification text: `KZT 1,234.00 with <card> ••<last4>`, a no-break space after the code (owner's phone, 2026-11-19). */
internal fun paymentText(currency: String, amount: String, card: String, last4: String): String =
    "$currency\u00A0${"%,.2f".format(Locale.US, BigDecimal(amount))} with $card ••$last4"

/** Kaspi's purchase notification text (owner's phone, a Kaspi Alaqan payment, 2026-11-19): amount with no-break-space thousands and `,` cents. */
internal fun kaspiText(amount: String, merchant: String, available: String): String {
    fun tenge(value: String) = "%,.2f".format(Locale.US, BigDecimal(value)).removeSuffix(".00").replace(',', '\u00A0').replace('.', ',') + " ₸"
    return "Покупка: ${tenge(amount)}\n$merchant\nДоступно: ${tenge(available)}"
}

private const val KASPI_PACKAGE = "kz.kaspi.mobile"

/**
 * Posts one Wallet-style payment notification, or a Kaspi-style one when installed as the `kaspi` flavor (title `Kaspi Gold`; extras
 * `merchant`, `amount`, `available`). From a shell (Kaspi: `-n kz.kaspi.mobile/dev.yernur.moneytracker.walletstub.PostReceiver`):
 * `adb shell am broadcast -a dev.yernur.moneytracker.walletstub.POST -n com.google.android.apps.walletnfcrel/dev.yernur.moneytracker.walletstub.PostReceiver --es merchant "Test Cafe" --es amount 1234.5`
 * Optional extras: `currency` (KZT), `card` (Visa), `last4` (1234), `text` (replaces the whole text line, for formats not seen yet: declines, refunds, foreign currency).
 */
class PostReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val merchant = intent.getStringExtra("merchant") ?: "Test Cafe"
        val kaspi = context.packageName == KASPI_PACKAGE
        val title = if (kaspi) intent.getStringExtra("card") ?: "Kaspi Gold" else merchant
        val text = intent.getStringExtra("text") ?: if (kaspi) kaspiText(intent.getStringExtra("amount") ?: "1630", merchant, intent.getStringExtra("available") ?: "1719.41") else paymentText(
            intent.getStringExtra("currency") ?: "KZT", intent.getStringExtra("amount") ?: "1234.00",
            intent.getStringExtra("card") ?: "Visa", intent.getStringExtra("last4") ?: "1234",
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Payments", NotificationManager.IMPORTANCE_DEFAULT))
        manager.notify(
            NOTIFICATION_ID,
            Notification.Builder(context, CHANNEL_ID).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(text).build(),
        )
    }
}
