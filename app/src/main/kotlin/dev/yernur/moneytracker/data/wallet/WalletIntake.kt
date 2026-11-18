package dev.yernur.moneytracker.data.wallet

import dev.yernur.moneytracker.data.AppSettings

/** A parsed payment and the time it was posted; [kaspi] when it came from Kaspi (its own default account) rather than Wallet. */
data class PendingPayment(val payment: WalletPayment, val postTime: Long, val kaspi: Boolean = false)

/** What is read from a Wallet notification. */
data class WalletRaw(val title: String?, val text: String?, val isGroupSummary: Boolean)

/** The privacy gate: nothing is read without the owner's consent, and only Wallet's and (separately consented) Kaspi's packages are ever read. */
class WalletIntake(private val settings: AppSettings, private val recent: RecentPayments = RecentPayments.shared) {
    /** [read] is called only when consent for that package is on, so other apps' notifications are never touched. */
    fun accept(packageName: String, postTime: Long, read: () -> WalletRaw): PendingPayment? {
        val parse: (String?, String?) -> WalletPayment? = when {
            packageName == WALLET_PACKAGE && settings.walletCapture.value -> WalletParser::parse
            packageName == KASPI_PACKAGE && settings.kaspiCapture.value -> KaspiParser::parse
            else -> return null
        }
        val raw = read()
        if (raw.isGroupSummary) return null
        val payment = parse(raw.title, raw.text) ?: return null
        if (!settings.firstSeenWalletPayment(WalletParser.dedupeKey(payment, postTime))) return null
        val pending = PendingPayment(payment, postTime, kaspi = packageName == KASPI_PACKAGE)
        return pending.takeIf { recent.firstFromAnySource(it) }
    }
}

/**
 * One payment announced by both apps (a Kaspi card paid through Google Wallet) gets one prompt: the second announcement of the same currency
 * and amount from the OTHER app within [WINDOW_MS] is dropped. Not seen yet (Kaspi posts nothing for a Wallet payment today, owner 2026-11-19),
 * so this is a guess at the shape; the first app to announce wins, with its default account. Kept in memory: the listener lives in the app's process.
 */
class RecentPayments {
    private val seen = ArrayDeque<PendingPayment>()

    @Synchronized
    fun firstFromAnySource(p: PendingPayment): Boolean {
        seen.removeAll { kotlin.math.abs(it.postTime - p.postTime) > WINDOW_MS }
        val twin = seen.any { it.kaspi != p.kaspi && it.payment.currency == p.payment.currency && it.payment.amount == p.payment.amount }
        if (!twin) seen.addLast(p)
        return !twin
    }

    companion object {
        const val WINDOW_MS = 3 * 60_000L
        val shared = RecentPayments()
    }
}
