package dev.yernur.moneytracker.data.wallet

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import dev.yernur.moneytracker.AppContainer
import dev.yernur.moneytracker.MainActivity
import dev.yernur.moneytracker.data.Money
import dev.yernur.moneytracker.data.db.AccountEntity
import kotlinx.coroutines.flow.first

/** The heads-up prompts for a Wallet payment: which account (first time only), then which category. */
class WalletPrompts(private val context: Context, private val container: AppContainer) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    suspend fun prompt(pending: PendingPayment) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return // nothing to show it with
        val accounts = WalletPlan.choosableAccounts(container.db.accountDao().observeLive().first())
        if (accounts.isEmpty()) return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Wallet payments", NotificationManager.IMPORTANCE_HIGH))
        when (val step = WalletPlan.decide(pending.payment, accounts, container.settings.walletAccountUid.value)) {
            WalletStep.AskAccount -> askAccount(pending, accounts)
            is WalletStep.CurrencyMismatch -> notify(pending, "Wallet payment not added", "${amount(pending)} is not in ${step.account.title}'s currency (${step.account.currencyCode}). Add it by hand.", openApp())
            is WalletStep.AskCategory -> askCategory(pending, step.account)
        }
    }

    private fun askAccount(pending: PendingPayment, accounts: List<AccountEntity>) {
        val actions = accounts.take(MAX_ACTIONS).map { account ->
            action(pending, account.title, WalletActionReceiver.PICK_ACCOUNT, WalletActionReceiver.EXTRA_UID, account.uid)
        }
        notify(
            pending, "Which account do you pay with?",
            "${amount(pending)} at ${pending.payment.merchant}. Saved as the default for Wallet payments (change it in More, Automation).",
            openPicker(pending), actions,
        )
    }

    private suspend fun askCategory(pending: PendingPayment, account: AccountEntity) {
        val top = WalletPlan.expenseCategories(container.db.categoryDao().observeLive().first()).take(MAX_ACTIONS - 1)
        val actions = top.map { category ->
            action(pending, category.title, WalletActionReceiver.SAVE, WalletActionReceiver.EXTRA_UID, category.uid)
        } + Notification.Action.Builder(icon(), "More…", openPicker(pending)).build() // a notification shows three buttons at most
        notify(pending, "${amount(pending)} · ${pending.payment.merchant}", "Pick a category · ${account.title}", openPicker(pending), actions)
    }

    /** A short confirmation that replaces the prompt once the expense is saved. */
    fun confirmSaved(pending: PendingPayment, accountTitle: String, categoryTitle: String) {
        manager.createNotificationChannel(NotificationChannel(CONFIRM_CHANNEL, "Wallet payments added", NotificationManager.IMPORTANCE_LOW))
        manager.notify(
            idOf(pending),
            Notification.Builder(context, CONFIRM_CHANNEL).setSmallIcon(android.R.drawable.ic_menu_save)
                .setContentTitle("Added: ${amount(pending)}").setContentText("$categoryTitle · $accountTitle")
                .setVisibility(Notification.VISIBILITY_PRIVATE).setTimeoutAfter(CONFIRM_TIMEOUT_MS).setAutoCancel(true).build(),
        )
    }

    private fun notify(pending: PendingPayment, title: String, text: String, content: PendingIntent, actions: List<Notification.Action> = emptyList()) {
        // The lock screen shows only a generic line: the amount and the shop are private.
        val public = Notification.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.ic_menu_edit).setContentTitle("Wallet payment").build()
        val builder = Notification.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle(title).setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
            .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(public)
            .setContentIntent(content).setAutoCancel(true)
            // Own group per payment: with four or more ungrouped notifications Android bundles them into a silent summary and nothing pops up.
            .setGroup("wallet-${idOf(pending)}")
        actions.forEach { builder.addAction(it) }
        manager.notify(idOf(pending), builder.build())
    }

    private fun action(pending: PendingPayment, label: String, what: String, extra: String, value: String): Notification.Action {
        val intent = pending.putInto(Intent(context, WalletActionReceiver::class.java).setAction(what).putExtra(extra, value))
        val pi = PendingIntent.getBroadcast(context, requestCode(pending, what, value), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Action.Builder(icon(), label, pi).build()
    }

    /** Opens the app on the category picker for this payment (the notification tap, and its "More..." button). */
    private fun openPicker(pending: PendingPayment): PendingIntent = PendingIntent.getActivity(
        context, requestCode(pending, "picker", ""), pending.putInto(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun icon() = Icon.createWithResource(context, android.R.drawable.ic_menu_add)
    private fun amount(pending: PendingPayment) = Money.format(pending.payment.amount, pending.payment.currency)

    companion object {
        private const val CHANNEL = "wallet_payments"
        private const val CONFIRM_CHANNEL = "wallet_payments_added"
        private const val MAX_ACTIONS = 3
        private const val CONFIRM_TIMEOUT_MS = 5_000L

        /** One notification per payment, so two quick payments do not replace each other. */
        fun idOf(pending: PendingPayment): Int = WalletParser.dedupeKey(pending.payment, pending.postTime).hashCode()
        private fun requestCode(pending: PendingPayment, what: String, value: String) = "${idOf(pending)}|$what|$value".hashCode()

        private const val X_CURRENCY = "wallet.currency"
        private const val X_AMOUNT = "wallet.amount"
        private const val X_MERCHANT = "wallet.merchant"
        private const val X_CARD = "wallet.card"
        private const val X_LAST4 = "wallet.last4"
        private const val X_TIME = "wallet.time"

        fun PendingPayment.putInto(intent: Intent): Intent = intent.putExtra(X_CURRENCY, payment.currency).putExtra(X_AMOUNT, payment.amount)
            .putExtra(X_MERCHANT, payment.merchant).putExtra(X_CARD, payment.cardName).putExtra(X_LAST4, payment.last4).putExtra(X_TIME, postTime)

        fun pendingFrom(intent: Intent): PendingPayment? {
            val currency = intent.getStringExtra(X_CURRENCY) ?: return null
            val merchant = intent.getStringExtra(X_MERCHANT) ?: return null
            if (!intent.hasExtra(X_AMOUNT) || !intent.hasExtra(X_TIME)) return null
            val payment = WalletPayment(
                currency, intent.getLongExtra(X_AMOUNT, 0), merchant, intent.getStringExtra(X_CARD).orEmpty(), intent.getStringExtra(X_LAST4).orEmpty(),
            )
            return PendingPayment(payment, intent.getLongExtra(X_TIME, 0))
        }
    }
}
