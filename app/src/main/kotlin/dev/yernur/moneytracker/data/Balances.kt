package dev.yernur.moneytracker.data

import dev.yernur.moneytracker.data.db.AccountEntity

/** Totals in [DEFAULT_CURRENCY] minor units. [counted] skips accounts hidden from the Total; [all] includes them. */
data class BalanceSummary(val counted: Long, val all: Long)

fun summarize(
    accounts: List<AccountEntity>,
    balances: Map<String, Long>,
    rates: Map<Pair<String, String>, Double>,
): BalanceSummary {
    var counted = 0L
    var all = 0L
    for (account in accounts) {
        if (!account.isActive || account.isArchived) continue
        // No known rate: the account is left out of the totals (unverified how the original treats this).
        val converted = Money.convert(balances[account.uid] ?: 0L, account.currencyCode, DEFAULT_CURRENCY, rates) ?: continue
        all += converted
        if (!account.ignoreInBalance) counted += converted
    }
    return BalanceSummary(counted, all)
}
