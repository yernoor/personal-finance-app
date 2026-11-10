package dev.yernur.moneytracker.ui

import android.app.NotificationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yernur.moneytracker.MoneyApp
import dev.yernur.moneytracker.data.Money
import dev.yernur.moneytracker.data.wallet.WalletPlan
import dev.yernur.moneytracker.data.wallet.WalletPrompts
import dev.yernur.moneytracker.data.wallet.WalletPrompts.Companion.pendingFrom
import kotlinx.coroutines.launch

/** The full list behind the notification's "More…" button (and its tap): the account first when none is chosen, then the category. */
class WalletPromptActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pending = pendingFrom(intent)
        if (pending == null) { finish(); return }
        val container = (application as MoneyApp).container
        val prompts = WalletPrompts(this, container)
        setContent {
            AppTheme {
                val scope = rememberCoroutineScope()
                val accounts by remember { container.db.accountDao().observeLive() }.collectAsStateWithLifecycle(emptyList())
                val categories by remember { container.db.categoryDao().observeLive() }.collectAsStateWithLifecycle(emptyList())
                val accountUid by container.settings.walletAccountUid.collectAsStateWithLifecycle()
                var done by remember { mutableStateOf(false) }
                val choosable = WalletPlan.choosableAccounts(accounts)
                val account = choosable.firstOrNull { it.uid == accountUid }
                val amount = Money.format(pending.payment.amount, pending.payment.currency)
                if (done) return@AppTheme
                AlertDialog(
                    onDismissRequest = { finish() },
                    title = { Text(if (account == null) "Which account do you pay with?" else "$amount · ${pending.payment.merchant}") },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            if (account == null) {
                                Text("Saved as the default for Wallet payments (change it in More, Automation).", Modifier.padding(bottom = 8.dp))
                                choosable.forEach { Text(it.title, Modifier.fillMaxWidth().clickable { container.settings.setWalletAccount(it.uid) }.padding(vertical = 12.dp)) }
                            } else {
                                Text("Pick a category · ${account.title}", Modifier.padding(bottom = 8.dp))
                                WalletPlan.expenseCategories(categories).forEach { category ->
                                    Text(
                                        category.title,
                                        Modifier.fillMaxWidth().clickable {
                                            done = true
                                            scope.launch {
                                                WalletPlan.save(container.ledger, pending, account.uid, category.uid)
                                                container.afterLedgerChange()
                                                prompts.confirmSaved(pending, account.title, category.title)
                                                finish()
                                            }
                                        }.padding(vertical = 12.dp),
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton({ getSystemService(NotificationManager::class.java).cancel(WalletPrompts.idOf(pending)); finish() }) { Text("Skip") } },
                )
            }
        }
    }
}
