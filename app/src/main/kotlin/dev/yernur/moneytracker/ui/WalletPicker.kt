package dev.yernur.moneytracker.ui

import android.app.NotificationManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yernur.moneytracker.LocalAppContainer
import dev.yernur.moneytracker.data.Money
import dev.yernur.moneytracker.data.Oklch
import dev.yernur.moneytracker.data.wallet.PendingPayment
import dev.yernur.moneytracker.data.wallet.WalletPlan
import dev.yernur.moneytracker.data.wallet.WalletPrompts
import kotlinx.coroutines.launch

/**
 * What opens in the app when a Wallet payment notification is tapped (or its "More…"): the account first when none is chosen, then the
 * category as icon tiles, like the Add screen. Shown over whatever screen the app is on; [onClose] clears it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WalletPickerDialog(pending: PendingPayment, onClose: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val prompts = remember { WalletPrompts(context, container) }
    // The icons follow the owner's icon style and accent, as in the rest of the app.
    val glass by container.settings.glass.collectAsStateWithLifecycle()
    val iconStyle by container.settings.iconStyle.collectAsStateWithLifecycle()
    val glassIconStyle by container.settings.glassIconStyle.collectAsStateWithLifecycle()
    val primary = MaterialTheme.colorScheme.primary
    val accentHue = remember(primary) { Oklch.hueOf(primary.toArgb()) }
    CompositionLocalProvider(LocalIconStyle provides if (glass) glassIconStyle else iconStyle, LocalAccentHue provides accentHue) {
        val scope = rememberCoroutineScope()
        val accounts by remember { container.db.accountDao().observeLive() }.collectAsStateWithLifecycle(emptyList())
        val categories by remember { container.db.categoryDao().observeLive() }.collectAsStateWithLifecycle(emptyList())
        val accountUid by container.settings.walletAccountUid.collectAsStateWithLifecycle()
        var saving by remember { mutableStateOf(false) }
        val choosable = WalletPlan.choosableAccounts(accounts)
        val account = choosable.firstOrNull { it.uid == accountUid }
        val amount = Money.format(pending.payment.amount, pending.payment.currency)
        if (saving) return@CompositionLocalProvider
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text(if (account == null) "Which account do you pay with?" else "$amount · ${pending.payment.merchant}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (account == null) {
                        Text("Saved as the default for Wallet payments (change it in More, Automation).", Modifier.padding(bottom = 8.dp))
                        choosable.forEach { Text(it.title, Modifier.fillMaxWidth().clickable { container.settings.setWalletAccount(it.uid) }.padding(vertical = 12.dp)) }
                    } else {
                        Text("Pick a category · ${account.title}", Modifier.padding(bottom = 8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            WalletPlan.expenseCategories(categories).forEach { category ->
                                CategoryTile(category, selected = false) {
                                    saving = true
                                    scope.launch {
                                        WalletPlan.save(container.ledger, pending, account.uid, category.uid)
                                        container.afterLedgerChange()
                                        prompts.confirmSaved(pending, account.title, category.title)
                                        onClose()
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton({ context.getSystemService(NotificationManager::class.java).cancel(WalletPrompts.idOf(pending)); onClose() }) { Text("Skip") }
            },
        )
    }
}
