package dev.yernur.moneytracker.data.wealth

import dev.yernur.moneytracker.data.DEFAULT_CURRENCY
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.BondEntity
import dev.yernur.moneytracker.data.db.GoalEntity
import dev.yernur.moneytracker.data.db.ManualAssetEntity
import dev.yernur.moneytracker.data.db.ManualKind
import dev.yernur.moneytracker.data.db.NetWorthSnapshotEntity
import dev.yernur.moneytracker.data.nowUtc
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.UUID

/** Totals in the default currency; [missingCurrencies] had no exchange rate yet and are NOT in the numbers. */
data class NetWorth(
    val accountsMinor: Long,
    val assetsMinor: Long,
    val liabilitiesMinor: Long,
    val missingCurrencies: Set<String>,
    /** Bond holdings at current value. Leave a broker account out of the accounts, or its bonds out of the portfolio, so nothing counts twice. */
    val investmentsMinor: Long = 0,
) {
    val totalMinor get() = accountsMinor + assetsMinor + investmentsMinor - liabilitiesMinor
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
        val investments = db.bondDao().getLive().sumOf { toDefault(it.currentValueMinor ?: it.costMinor, it.currencyCode) }
        return NetWorth(accounts, sum(ManualKind.Asset), sum(ManualKind.Liability), missing, investments)
    }

    /** One row per day; a second call the same day replaces it. Skipped while a rate is missing, so a gap never looks like a loss. */
    suspend fun recordSnapshot(today: LocalDate): Boolean {
        val now = currentNetWorth()
        if (now.missingCurrencies.isNotEmpty()) return false
        db.netWorthSnapshotDao().put(NetWorthSnapshotEntity(today.toString(), now.accountsMinor, now.assetsMinor, now.liabilitiesMinor, now.investmentsMinor))
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

    suspend fun saveBond(uid: String?, bond: BondEntity) {
        val now = nowUtc()
        val existing = uid?.let { id -> db.bondDao().getLive().firstOrNull { it.uid == id } }
        db.bondDao().upsert(bond.copy(uid = existing?.uid ?: UUID.randomUUID().toString(), created = existing?.created ?: now, modified = now, isRemoved = false))
    }

    suspend fun deleteBond(uid: String) {
        val item = db.bondDao().getLive().firstOrNull { it.uid == uid } ?: return
        db.bondDao().upsert(item.copy(isRemoved = true, modified = nowUtc()))
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

    /** All live bonds with their own numbers, plus portfolio totals in the default currency (a bond whose currency has no rate yet is left out of the totals). */
    suspend fun portfolio(today: LocalDate): Portfolio {
        val rates = fx.rates.first()
        val bonds = db.bondDao().getLive().map { it to summarizeBond(it, today) }
        var value = 0L
        var cost = 0L
        var annual = 0L
        val flows = mutableListOf<CashFlow>()
        val missing = sortedSetOf<String>()
        for ((bond, summary) in bonds) {
            fun toDefault(minor: Long) = convertViaKzt(minor, bond.currencyCode, DEFAULT_CURRENCY, rates)
            val v = toDefault(bond.currentValueMinor ?: bond.costMinor)
            if (v == null) {
                missing += bond.currencyCode
                continue
            }
            value += v
            cost += toDefault(bond.costMinor) ?: 0L
            annual += toDefault(summary.annualCouponMinor) ?: 0L
            summary.remaining.forEach { flows += CashFlow(it.date, toDefault(it.couponMinor) ?: 0L, toDefault(it.principalMinor) ?: 0L) }
        }
        return Portfolio(bonds, value, cost, annual, ladder(flows), missing)
    }
}

/** Totals in the default currency. */
data class Portfolio(
    val bonds: List<Pair<BondEntity, BondSummary>>,
    val valueMinor: Long,
    val costMinor: Long,
    val annualCouponMinor: Long,
    val ladder: Map<Int, LadderYear>,
    val missingCurrencies: Set<String>,
) {
    val gainMinor get() = valueMinor - costMinor
}
