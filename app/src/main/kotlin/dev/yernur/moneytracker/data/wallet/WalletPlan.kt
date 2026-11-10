package dev.yernur.moneytracker.data.wallet

import dev.yernur.moneytracker.data.LedgerRepository
import dev.yernur.moneytracker.data.TransactionDraft
import dev.yernur.moneytracker.data.db.AccountEntity
import dev.yernur.moneytracker.data.db.CategoryEntity
import dev.yernur.moneytracker.data.db.TransactionType
import java.time.Instant
import java.time.ZoneId

/** What to do with a new payment. */
sealed interface WalletStep {
    /** No default account yet (or it was deleted): ask which one, and remember the answer. */
    data object AskAccount : WalletStep

    /** The payment's currency is not the account's. Converting is not done for Wallet payments yet (foreign-currency format unseen), so the owner adds it by hand. */
    data class CurrencyMismatch(val account: AccountEntity) : WalletStep

    data class AskCategory(val account: AccountEntity) : WalletStep
}

object WalletPlan {
    fun decide(payment: WalletPayment, accounts: List<AccountEntity>, defaultUid: String?): WalletStep {
        val account = accounts.firstOrNull { it.uid == defaultUid } ?: return WalletStep.AskAccount
        return if (account.currencyCode == payment.currency) WalletStep.AskCategory(account) else WalletStep.CurrencyMismatch(account)
    }

    /** Accounts a payment can go to: not archived. */
    fun choosableAccounts(all: List<AccountEntity>): List<AccountEntity> = all.filter { !it.isArchived && !it.isRemoved }

    /** Expense categories, most used first (the prompt shows the first few as buttons). */
    fun expenseCategories(all: List<CategoryEntity>): List<CategoryEntity> = all
        .filter { it.type == TransactionType.Expense && !it.isArchived && !it.isRemoved }
        .sortedWith(compareByDescending<CategoryEntity> { it.useCount }.thenBy { it.position ?: Int.MAX_VALUE }.thenBy { it.title })

    /** The day the payment was made, in the phone's time zone (what History groups by). */
    fun localDate(postTime: Long, zone: ZoneId = ZoneId.systemDefault()): String = Instant.ofEpochMilli(postTime).atZone(zone).toLocalDate().toString()

    /** The expense that is saved once the owner picked a category: the merchant goes in the comment. */
    suspend fun save(ledger: LedgerRepository, pending: PendingPayment, accountUid: String, categoryUid: String, zone: ZoneId = ZoneId.systemDefault()) {
        ledger.saveTransaction(
            TransactionDraft(
                uid = null, type = TransactionType.Expense, accountUid = accountUid, categoryUid = categoryUid,
                amount = pending.payment.amount, date = localDate(pending.postTime, zone), comment = pending.payment.merchant, tagUids = emptySet(),
            )
        )
    }
}
