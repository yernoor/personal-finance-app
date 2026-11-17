package dev.yernur.moneytracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yernur.moneytracker.LocalAppContainer
import dev.yernur.moneytracker.data.AffordResult
import dev.yernur.moneytracker.data.DEFAULT_CURRENCY
import dev.yernur.moneytracker.data.Money
import dev.yernur.moneytracker.data.Verdict
import kotlinx.coroutines.launch

/**
 * "Can I afford ₸ ___ ?" on Analysis -> Overview, right under the month figure it is based on (owner's choice 2026-11-18; it was a screen
 * in More). Checks against the main account with no category; Details opens the full screen with the price filled in, to pick a category
 * or another account.
 */
@Composable
fun AffordCard(onDetails: (price: String) -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val accounts by remember { container.db.accountDao().observeLive() }.collectAsStateWithLifecycle(emptyList())
    val account = accounts.filter { it.isActive && !it.isArchived }.let { usable -> usable.firstOrNull { it.uid == "main" } ?: usable.firstOrNull() }
    var price by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<AffordResult?>(null) }
    var failed by remember { mutableStateOf(false) }
    val minor = Money.parseMinor(price)?.takeIf { it > 0 }
    fun check() {
        val amount = minor ?: return
        val from = account ?: return
        scope.launch {
            result = container.afford.check(amount, null, from.uid)
            failed = result == null
        }
    }
    AppCard(Modifier.fillMaxWidth().testTag("afford-card")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Can I afford it?", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    price, { price = it; result = null; failed = false }, Modifier.weight(1f).testTag("afford-card-price"), singleLine = true,
                    label = { Text("Price") }, suffix = { Text(DEFAULT_CURRENCY) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { check() }),
                )
                Button(::check, Modifier.height(56.dp).testTag("afford-card-check"), enabled = minor != null && account != null) { Text("Check") }
            }
            if (failed) Text("No exchange rate is known for the main account, so its balance cannot be compared.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            result?.let { r ->
                val scheme = MaterialTheme.colorScheme
                val (bg, fg, label) = when (r.verdict) {
                    Verdict.Yes -> Triple(scheme.primaryContainer, scheme.onPrimaryContainer, "YES")
                    Verdict.Tight -> Triple(scheme.tertiaryContainer, scheme.onTertiaryContainer, "TIGHT")
                    Verdict.No -> Triple(scheme.errorContainer, scheme.onErrorContainer, "NO")
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        label, Modifier.background(bg, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp).testTag("afford-card-verdict"),
                        style = MaterialTheme.typography.labelLarge, color = fg,
                    )
                    Text(r.headline(null), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "About ${Money.formatRounded(r.afterPurchase.coerceAtLeast(0), DEFAULT_CURRENCY)} stays free until ${r.horizon} · from ${account?.title?.ifBlank { "Main" }}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton({ onDetails(price) }, Modifier.align(Alignment.End).testTag("afford-card-details")) {
                Text(if (result != null) "Details" else "Category, account and more")
            }
        }
    }
}
