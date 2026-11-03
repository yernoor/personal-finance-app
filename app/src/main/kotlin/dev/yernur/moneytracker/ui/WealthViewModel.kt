package dev.yernur.moneytracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.yernur.moneytracker.AppContainer
import dev.yernur.moneytracker.data.db.AccountEntity
import dev.yernur.moneytracker.data.db.BondEntity
import dev.yernur.moneytracker.data.db.CashflowItemEntity
import dev.yernur.moneytracker.data.wealth.WealthOverview
import dev.yernur.moneytracker.data.wealth.toDefault
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class WealthViewModel(private val container: AppContainer) : ViewModel() {
    private val wealth = container.wealth

    /** Rebuilt whenever an account, item, bond, goal, snapshot or rate changes. Null until the first build. */
    val overview: StateFlow<WealthOverview?> = wealth.changes().onStart { emit(Unit) }
        .mapLatest { wealth.overview(LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val accounts: StateFlow<List<AccountEntity>> = container.db.accountDao().observeLive()
        .mapLatest { list -> list.filter { it.isActive && !it.isArchived } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveAsset(uid: String?, draft: AssetDraft) = viewModelScope.launch { wealth.saveManualAsset(uid, draft.title, draft.kind, draft.currency, draft.valueMinor) }

    fun deleteAsset(uid: String) = viewModelScope.launch { wealth.deleteManualAsset(uid) }

    /** [draft]'s target is in [currency] (the one on screen); goals are stored in the default currency. */
    fun saveGoal(uid: String?, draft: GoalDraft, currency: String) = viewModelScope.launch {
        val rates = container.fx.rates.first()
        val target = toDefault(draft.targetMinor, currency, rates) ?: draft.targetMinor
        val account = draft.accountTitle?.let { title -> accounts.value.firstOrNull { it.title == title } }
        wealth.saveGoal(uid, draft.title, target, draft.deadline, account?.uid)
    }

    fun deleteGoal(uid: String) = viewModelScope.launch { wealth.deleteGoal(uid) }

    fun saveBond(uid: String?, bond: BondEntity) = viewModelScope.launch { wealth.saveBond(uid, bond) }

    fun deleteBond(uid: String) = viewModelScope.launch { wealth.deleteBond(uid) }

    fun saveCashflow(uid: String?, item: CashflowItemEntity) = viewModelScope.launch { wealth.saveCashflowItem(uid, item) }

    fun deleteCashflow(uid: String) = viewModelScope.launch { wealth.deleteCashflowItem(uid) }
}
