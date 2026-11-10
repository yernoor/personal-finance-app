package dev.yernur.moneytracker.data.wallet

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.yernur.moneytracker.MoneyApp
import dev.yernur.moneytracker.data.wallet.WalletPrompts.Companion.pendingFrom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The buttons on the prompt notifications: choose the default account, or choose the category (which saves the expense). */
class WalletActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = pendingFrom(intent) ?: return
        val uid = intent.getStringExtra(EXTRA_UID) ?: return
        val container = (context.applicationContext as MoneyApp).container
        val prompts = WalletPrompts(context, container)
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    PICK_ACCOUNT -> {
                        container.settings.setWalletAccount(uid)
                        prompts.prompt(pending) // now the category question, in the same notification slot
                    }
                    SAVE -> {
                        val accountUid = container.settings.walletAccountUid.value ?: return@launch
                        WalletPlan.save(container.ledger, pending, accountUid, uid)
                        container.afterLedgerChange()
                        val account = container.db.accountDao().get(accountUid)?.title.orEmpty()
                        val category = container.db.categoryDao().get(uid)?.title.orEmpty()
                        prompts.confirmSaved(pending, account, category)
                    }
                    else -> context.getSystemService(NotificationManager::class.java).cancel(WalletPrompts.idOf(pending))
                }
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        const val PICK_ACCOUNT = "dev.yernur.moneytracker.wallet.PICK_ACCOUNT"
        const val SAVE = "dev.yernur.moneytracker.wallet.SAVE"
        const val EXTRA_UID = "wallet.uid"
    }
}
