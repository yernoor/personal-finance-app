package dev.yernur.moneytracker.ui

import android.app.NotificationManager
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yernur.moneytracker.LocalAppContainer
import dev.yernur.moneytracker.data.Money
import dev.yernur.moneytracker.data.Oklch
import dev.yernur.moneytracker.data.db.AccountEntity
import dev.yernur.moneytracker.data.db.CategoryEntity
import dev.yernur.moneytracker.data.wallet.PendingPayment
import dev.yernur.moneytracker.data.wallet.WalletPlan
import dev.yernur.moneytracker.data.wallet.WalletPrompts
import kotlinx.coroutines.launch

private const val GRID_COLUMNS = 4

/**
 * What opens in the app when a Wallet payment notification is tapped (or its "More…"): a sheet from the bottom with the payment, the account
 * (the default, or another one for this payment only) and the categories as icon tiles. The very first time it asks which account
 * to use and keeps the answer as the default. Shown over whatever screen the app is on; [onClose] clears it.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
    // Shown over the app from outside AppRoot, so it must say itself that glass is on: without it the sheet was opaque and nothing blurred.
    CompositionLocalProvider(LocalGlass provides glass, LocalIconStyle provides if (glass) glassIconStyle else iconStyle, LocalAccentHue provides accentHue) {
        val scope = rememberCoroutineScope()
        val accounts by remember { container.db.accountDao().observeLive() }.collectAsStateWithLifecycle(emptyList())
        val categories by remember { container.db.categoryDao().observeLive() }.collectAsStateWithLifecycle(emptyList())
        val defaultUid by container.settings.walletAccountUid.collectAsStateWithLifecycle()
        var otherUid by rememberSaveable { mutableStateOf<String?>(null) } // an account picked for this payment only
        var selectedUid by rememberSaveable { mutableStateOf<String?>(null) } // a tap only selects: nothing is saved until Add
        var saving by remember { mutableStateOf(false) }
        var closed by remember { mutableStateOf(false) }
        val choosable = WalletPlan.choosableAccounts(accounts)
        val defaultAccount = choosable.firstOrNull { it.uid == defaultUid }
        val account = choosable.firstOrNull { it.uid == otherUid } ?: defaultAccount
        if (closed) return@CompositionLocalProvider

        fun skip() { context.getSystemService(NotificationManager::class.java).cancel(WalletPrompts.idOf(pending)); onClose() }

        ModalBottomSheet(
            onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false), dragHandle = null,
            containerColor = glassContainer(BottomSheetDefaults.ContainerColor),
        ) {
            GlassWindowBlur()
            IconButton(onClose, Modifier.padding(start = 8.dp).testTag("wallet-collapse")) { Icon(Icons.Outlined.KeyboardArrowDown, "Collapse") }
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Text(Money.format(pending.payment.amount, pending.payment.currency), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                    Text(paidWith(pending), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // The first-time account question gives way to the category grid with a slide and fade, and the sheet's height follows, instead of a cut.
                AnimatedContent(
                    account == null,
                    transitionSpec = { (slideInHorizontally(tween(320)) { it / 4 } + fadeIn(tween(320))) togetherWith (slideOutHorizontally(tween(220)) { -it / 4 } + fadeOut(tween(160))) },
                    label = "wallet-step",
                ) { asking ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (asking) AccountQuestion(choosable) { container.settings.setWalletAccount(it.uid) } else if (account != null) {
                    Text("Account", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        choosable.forEach { FilterChip(it.uid == account.uid, { otherUid = it.uid }, label = { Text(it.title) }, modifier = Modifier.testTag("wallet-account-${it.uid}")) }
                    }
                    Text("Category", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val all = WalletPlan.expenseCategories(categories)
                    var expanded by rememberSaveable { mutableStateOf(false) }
                    val shown = visibleCategories(all, expanded, selectedUid)
                    val expandTile = all.size > COLLAPSED_CATEGORIES
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        (shown.map<_, Any> { it } + if (expandTile) listOf(Unit) else emptyList()).chunked(GRID_COLUMNS).forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { item ->
                                    val category = item as? CategoryEntity
                                    if (category == null) { ExpandTile(expanded, Modifier.weight(1f).padding(vertical = 6.dp)) { expanded = !expanded }; return@forEach }
                                    Column(
                                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { selectedUid = if (selectedUid == category.uid) null else category.uid }
                                            .padding(vertical = 6.dp).testTag("wallet-category-${category.uid}"),
                                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        IconBadge(category.icon, category.color, size = 52.dp, ringColor = if (selectedUid == category.uid) MaterialTheme.colorScheme.primary else null)
                                        Text(category.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                }
                }
                // Skip until a category is selected, then Add in its place: saving takes a deliberate second tap, so a stray touch cannot add an entry.
                val selected = account?.let { chosen -> WalletPlan.expenseCategories(categories).firstOrNull { it.uid == selectedUid }?.let { chosen to it } }
                AnimatedContent(selected, transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) }, label = "wallet-action") { choice ->
                    if (choice == null) FilledTonalButton(::skip, Modifier.fillMaxWidth().testTag("wallet-skip")) { Text("Skip") }
                    else Button(
                        {
                            saving = true
                            val (to, category) = choice
                            scope.launch {
                                WalletPlan.save(container.ledger, pending, to.uid, category.uid)
                                container.afterLedgerChange()
                                prompts.confirmSaved(pending, to.title, category.title)
                                closed = true
                                onClose()
                            }
                        },
                        Modifier.fillMaxWidth().testTag("wallet-add"), enabled = !saving,
                    ) { Text("Add") }
                }
            }
        }
    }
}

/** The first-time question: which account Wallet payments go to by default. */
@Composable
private fun AccountQuestion(accounts: List<AccountEntity>, onPick: (AccountEntity) -> Unit) {
    Text("Which account do you pay with?", style = MaterialTheme.typography.titleMedium)
    Text("Saved as the default for Wallet payments. Change it in More, Automation.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    accounts.forEach { account ->
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onPick(account) }.padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconBadge(account.icon, account.color, size = 40.dp)
            Text(account.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(account.currencyCode, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun paidWith(pending: PendingPayment): String {
    val card = listOf(pending.payment.cardName, pending.payment.last4.takeIf { it.isNotEmpty() }?.let { "••$it" }).filterNot { it.isNullOrEmpty() }.joinToString(" ")
    return listOf(pending.payment.merchant, card).filter { it.isNotEmpty() }.joinToString(" · ")
}
