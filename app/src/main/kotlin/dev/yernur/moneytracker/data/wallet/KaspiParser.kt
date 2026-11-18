package dev.yernur.moneytracker.data.wallet

import dev.yernur.moneytracker.data.Money

/** Kaspi.kz's app. Seen on the owner's phone (Wallet logger export, 2026-11-19). */
const val KASPI_PACKAGE = "kz.kaspi.mobile"

/**
 * Kaspi's purchase notification: title = card (`Kaspi Gold`), text = `Покупка: 1 630 ₸` / merchant / `Доступно: ...` on three lines
 * (one sample, a Kaspi Alaqan payment, 2026-11-19; space thousands, `,` decimals, ₸ after the amount). Top-ups (`Пополнение`), transit tickets
 * (no amount) and anything else return null. Other currencies are unseen, so only ₸ is read.
 */
object KaspiParser {
    private val text = Regex("""Покупка:\s*(\d{1,3}(?:[ \u00A0\u202F]\d{3})*(?:,\d{1,2})?)\s*₸\s*\n\s*([^\n]+)[\s\S]*""")

    fun parse(title: String?, body: String?): WalletPayment? {
        val match = text.matchEntire(body?.trim() ?: return null) ?: return null
        val (amountText, merchant) = match.destructured
        val amount = Money.parseMinor(amountText.replace(Regex("[ \u00A0\u202F]"), "").replace(',', '.'))?.takeIf { it > 0 } ?: return null
        return WalletPayment("KZT", amount, merchant.trim(), title?.trim().orEmpty().ifEmpty { "Kaspi" }, "")
    }
}
