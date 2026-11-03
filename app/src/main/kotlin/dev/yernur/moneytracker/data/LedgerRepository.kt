package dev.yernur.moneytracker.data

import androidx.room.withTransaction
import dev.yernur.moneytracker.data.db.AccountEntity
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.CategoryEntity
import dev.yernur.moneytracker.data.db.TagEntity
import dev.yernur.moneytracker.data.db.TransactionEntity
import dev.yernur.moneytracker.data.db.TransactionTagEntity
import dev.yernur.moneytracker.data.db.TransactionType
import dev.yernur.moneytracker.data.db.TransferEntity
import java.util.UUID
import kotlinx.coroutines.flow.first

/** `uid == null` creates a new row; otherwise the existing row is edited. Amounts are in account-currency minor units. */
data class TransactionDraft(
    val uid: String?,
    val type: TransactionType,
    val accountUid: String,
    val categoryUid: String?,
    val amount: Long,
    val date: String,
    val comment: String?,
    val tagUids: Set<String>,
)

/** [toAmount] is required when the two accounts have different currencies. */
data class TransferDraft(
    val uid: String?,
    val fromAccountUid: String,
    val toAccountUid: String,
    val fromAmount: Long,
    val toAmount: Long?,
    val date: String,
    val comment: String?,
)

/** The single write path: stamps `modified`, keeps tombstones, and fills the derived amount fields. */
class LedgerRepository(
    private val db: AppDatabase,
    private val now: () -> String = ::nowUtc,
    private val newUid: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun saveTransaction(draft: TransactionDraft) = db.withTransaction {
        val account = requireNotNull(db.accountDao().get(draft.accountUid)) { "unknown account" }
        val rates = db.rateDao().getAll().associate { (it.fromCurrencyCode to it.toCurrencyCode) to it.value }
        val existing = draft.uid?.let { db.transactionDao().get(it) }
        val stamp = now()
        val uid = existing?.uid ?: newUid()
        val sameAmount = existing?.amountInAccountCurrency == draft.amount
        db.transactionDao().upsert(
            TransactionEntity(
                uid = uid, type = draft.type, accountUid = account.uid, categoryUid = draft.categoryUid,
                amountInDefaultCurrency = Money.convert(draft.amount, account.currencyCode, DEFAULT_CURRENCY, rates),
                // A real-currency amount only means something for the amount it was entered with.
                amountInRealCurrency = existing?.amountInRealCurrency?.takeIf { sameAmount },
                realCurrencyCode = existing?.realCurrencyCode?.takeIf { sameAmount },
                amountInAccountCurrency = draft.amount, accountCurrencyCode = account.currencyCode,
                date = draft.date, comment = draft.comment?.takeIf { it.isNotBlank() },
                created = existing?.created ?: stamp, modified = stamp,
            )
        )
        db.transactionDao().clearTags(uid)
        db.transactionDao().insertTagLinks(draft.tagUids.map { TransactionTagEntity(uid, it) })
        if (existing == null) {
            draft.categoryUid?.let { db.categoryDao().bumpUsage(it, stamp) }
            if (draft.tagUids.isNotEmpty()) db.tagDao().bumpUsage(draft.tagUids, stamp)
        }
    }

    /**
     * Makes the account's balance equal [target] by adding an Income/Expense for the difference with no category, which is how backups
     * store an "overwrite balance" (verified: the export has such rows). Returns the signed difference, 0 when nothing changed.
     */
    suspend fun overwriteBalance(accountUid: String, target: Long, date: String): Long = db.withTransaction {
        val current = db.accountDao().observeBalances().first().firstOrNull { it.uid == accountUid }?.balance ?: 0L
        val diff = target - current
        if (diff != 0L) {
            val type = if (diff > 0) TransactionType.Income else TransactionType.Expense
            saveTransaction(TransactionDraft(null, type, accountUid, null, kotlin.math.abs(diff), date, null, emptySet()))
        }
        diff
    }

    suspend fun deleteTransaction(uid: String) = db.withTransaction {
        db.transactionDao().get(uid)?.let { db.transactionDao().upsert(it.copy(isRemoved = true, modified = now())) }
    }

    suspend fun saveTransfer(draft: TransferDraft) = db.withTransaction {
        val from = requireNotNull(db.accountDao().get(draft.fromAccountUid)) { "unknown account" }
        val to = requireNotNull(db.accountDao().get(draft.toAccountUid)) { "unknown account" }
        require(from.uid != to.uid) { "transfer needs two different accounts" }
        val crossCurrency = from.currencyCode != to.currencyCode
        require(!crossCurrency || draft.toAmount != null) { "cross-currency transfer needs the received amount" }
        val existing = draft.uid?.let { db.transferDao().get(it) }
        val stamp = now()
        db.transferDao().upsert(
            TransferEntity(
                uid = existing?.uid ?: newUid(), fromAccountUid = from.uid, toAccountUid = to.uid,
                fromAmount = draft.fromAmount, fromCurrencyCode = from.currencyCode,
                // Same-currency transfers leave the received side empty, as the backup does.
                toAmount = draft.toAmount.takeIf { crossCurrency }, toCurrencyCode = to.currencyCode.takeIf { crossCurrency },
                date = draft.date, comment = draft.comment?.takeIf { it.isNotBlank() },
                created = existing?.created ?: stamp, modified = stamp,
            )
        )
    }

    suspend fun deleteTransfer(uid: String) = db.withTransaction {
        db.transferDao().get(uid)?.let { db.transferDao().upsert(it.copy(isRemoved = true, modified = now())) }
    }

    suspend fun saveAccount(account: AccountEntity) {
        val stamp = now()
        db.accountDao().upsert(account.copy(created = account.created.ifEmpty { stamp }, modified = stamp))
    }

    suspend fun saveCategory(category: CategoryEntity) {
        val stamp = now()
        db.categoryDao().upsert(category.copy(created = category.created.ifEmpty { stamp }, modified = stamp))
    }

    suspend fun deleteCategory(uid: String) {
        db.categoryDao().get(uid)?.let { db.categoryDao().upsert(it.copy(isRemoved = true, modified = now())) }
    }

    /** Returns the existing tag with this name (case-insensitive) or creates it. */
    suspend fun tagNamed(name: String): TagEntity {
        val clean = name.trim()
        // Compared in Kotlin: SQLite's NOCASE folds ASCII only, and the tags are Cyrillic.
        db.tagDao().getLive().firstOrNull { it.name.equals(clean, ignoreCase = true) }?.let { return it }
        val stamp = now()
        return TagEntity(newUid(), clean, 0, null, stamp, stamp).also { db.tagDao().upsert(it) }
    }

    suspend fun renameTag(uid: String, name: String) {
        db.tagDao().get(uid)?.let { db.tagDao().upsert(it.copy(name = name.trim(), modified = now())) }
    }

    suspend fun deleteTag(uid: String) {
        db.tagDao().get(uid)?.let { db.tagDao().upsert(it.copy(isRemoved = true, modified = now())) }
    }

    fun newAccountUid(): String = newUid()
    fun newCategoryUid(): String = newUid()
}
