package dev.yernur.moneytracker.data.wealth

import dev.yernur.moneytracker.data.DEFAULT_CURRENCY
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.GoalEntity
import dev.yernur.moneytracker.data.db.ManualAssetEntity
import dev.yernur.moneytracker.data.db.ManualKind
import dev.yernur.moneytracker.data.db.NetWorthSnapshotEntity
import dev.yernur.moneytracker.data.nowUtc
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.UUID

/** Totals in the default currency; [missingCurrencies] had no exchange rate yet and are NOT in the numbers. */
data class NetWorth(val accountsMinor: Long, val assetsMinor: Long, val liabilitiesMinor: Long, val missingCurrencies: Set<String>) {
    val totalMinor get() = accountsMinor + assetsMinor - liabilitiesMinor
}

class WealthRepository(private val db: AppDatabase, private val fx: FxRepository) {
    /** Net worth now: every active account (also those hidden from the headline Total) plus manual assets minus liabilities. */
    suspend fun currentNetWorth(): NetWorth {
        val rates = fx.rates.first()
        val missing = sortedSetOf<String>()
        fun toDefault(minor: Long, currency: String): Long {
            val converted = convertViaKzt(minor, currency, DEFAULT_CURRENCY, rates)
            if (converted == null) missing += currency
            return converted ?: 0L
        }
        val balances = db.accountDao().observeBalances().first().associate { it.uid to it.balance }
        val accounts = db.accountDao().getAll().filter { !it.isRemoved && it.isActive && !it.isArchived }
            .sumOf { toDefault(balances[it.uid] ?: 0L, it.currencyCode) }
        val manual = db.manualAssetDao().getLive()
        fun sum(kind: ManualKind) = manual.filter { it.kind == kind }.sumOf { toDefault(it.valueMinor, it.currencyCode) }
        return NetWorth(accounts, sum(ManualKind.Asset), sum(ManualKind.Liability), missing)
    }

    /** One row per day; a second call the same day replaces it. Skipped while a rate is missing, so a gap never looks like a loss. */
    suspend fun recordSnapshot(today: LocalDate): Boolean {
        val now = currentNetWorth()
        if (now.missingCurrencies.isNotEmpty()) return false
        db.netWorthSnapshotDao().put(NetWorthSnapshotEntity(today.toString(), now.accountsMinor, now.assetsMinor, now.liabilitiesMinor))
        return true
    }

    suspend fun saveManualAsset(uid: String?, title: String, kind: ManualKind, currency: String, valueMinor: Long) {
        val now = nowUtc()
        val existing = uid?.let { id -> db.manualAssetDao().getLive().firstOrNull { it.uid == id } }
        db.manualAssetDao().upsert(
            ManualAssetEntity(existing?.uid ?: UUID.randomUUID().toString(), title, kind, currency, valueMinor, existing?.created ?: now, now),
        )
    }

    suspend fun deleteManualAsset(uid: String) {
        val item = db.manualAssetDao().getLive().firstOrNull { it.uid == uid } ?: return
        db.manualAssetDao().upsert(item.copy(isRemoved = true, modified = nowUtc()))
    }

    suspend fun saveGoal(uid: String?, title: String, targetMinor: Long, targetDate: LocalDate?, accountUid: String?) {
        val now = nowUtc()
        val existing = uid?.let { id -> db.goalDao().getLive().firstOrNull { it.uid == id } }
        db.goalDao().upsert(
            GoalEntity(existing?.uid ?: UUID.randomUUID().toString(), title, targetMinor, targetDate?.toString(), accountUid, existing?.created ?: now, now),
        )
    }

    suspend fun deleteGoal(uid: String) {
        val item = db.goalDao().getLive().firstOrNull { it.uid == uid } ?: return
        db.goalDao().upsert(item.copy(isRemoved = true, modified = nowUtc()))
    }

    /** Every live goal measured against net worth, or against its account's balance in the default currency. */
    suspend fun goalProgress(today: LocalDate, annualReturnPercent: java.math.BigDecimal = java.math.BigDecimal.ZERO): List<Pair<GoalEntity, GoalProgress>> {
        val netWorth = currentNetWorth().totalMinor
        val rates = fx.rates.first()
        val balances = db.accountDao().observeBalances().first().associate { it.uid to it.balance }
        val accounts = db.accountDao().getAll().associateBy { it.uid }
        return db.goalDao().getLive().map { goal ->
            val current = if (goal.accountUid == null) netWorth else {
                val account = accounts[goal.accountUid]
                if (account == null) 0L else convertViaKzt(balances[account.uid] ?: 0L, account.currencyCode, DEFAULT_CURRENCY, rates) ?: 0L
            }
            goal to goalProgress(current, goal.targetMinor, goal.targetDate?.let(LocalDate::parse), today, annualReturnPercent)
        }
    }
}
