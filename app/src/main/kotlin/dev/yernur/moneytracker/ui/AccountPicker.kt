package dev.yernur.moneytracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.yernur.moneytracker.data.Money
import dev.yernur.moneytracker.data.db.AccountEntity

private val TILE_SHAPE = RoundedCornerShape(18.dp)

private fun AccountEntity.shownTitle() = title.ifBlank { "Main" }

/** One account as a tile: its icon, name and balance. [dimmed] marks the account the other side of a transfer already uses; a refused tap shakes it. */
@Composable
fun AccountTile(
    account: AccountEntity, balance: Long?, selected: Boolean, tag: String, modifier: Modifier = Modifier,
    dimmed: Boolean = false, rejection: Rejection? = null, onClick: () -> Unit,
) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(rejection) {
        if (rejection?.uid == account.uid) for (x in listOf(-14f, 12f, -8f, 5f, 0f)) shake.animateTo(x, tween(45))
    }
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier.width(112.dp).graphicsLayer { translationX = shake.value }.alpha(if (dimmed) 0.42f else 1f).clip(TILE_SHAPE)
            .background(if (selected) scheme.secondaryContainer else scheme.surface)
            .border(if (selected) BorderStroke(2.dp, scheme.primary) else BorderStroke(1.dp, scheme.outlineVariant), TILE_SHAPE)
            .selectable(selected, role = Role.RadioButton, onClick = onClick).testTag(tag),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            IconBadge(account.icon, account.color, size = 40.dp)
            Text(account.shownTitle(), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(balance?.takeIf { !account.ignoreInBalance }?.let { Money.format(it, account.currencyCode) } ?: account.currencyCode, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1)
        }
        if (account.ignoreInBalance) Icon(Icons.Outlined.VisibilityOff, "Hidden from the total", Modifier.align(Alignment.TopEnd).padding(8.dp).size(16.dp), tint = scheme.onSurfaceVariant)
    }
}

/** Every account as a scrolling row of tiles (transfers: From and To). [blockedUid] is the account the other side holds. */
@Composable
fun AccountStrip(
    accounts: List<AccountEntity>, balances: Map<String, Long>, selected: String?, tagPrefix: String,
    blockedUid: String? = null, rejection: Rejection? = null, onPick: (String) -> Unit,
) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        accounts.forEach { account ->
            AccountTile(
                account, balances[account.uid], account.uid == selected, "$tagPrefix-${account.uid}",
                dimmed = account.uid == blockedUid && account.uid != selected, rejection = rejection,
            ) { onPick(account.uid) }
        }
    }
}

/**
 * The account of an expense or income. With at most one visible account (the others hidden from the total) it is one line showing the
 * chosen account, and a tap on it opens every account; picking one closes it again. With several visible accounts it is their row of
 * tiles, plus one tile that reveals the hidden ones.
 */
@Composable
fun AccountPicker(accounts: List<AccountEntity>, balances: Map<String, Long>, selected: String?, tagPrefix: String, onPick: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val visible = accounts.filter { !it.ignoreInBalance }
    val chosen = accounts.firstOrNull { it.uid == selected }
    if (visible.size <= 1) {
        val turn by animateFloatAsState(if (expanded) 180f else 0f, tween(350), label = "account-chevron")
        Column {
            Row(
                Modifier.fillMaxWidth().clip(TILE_SHAPE).background(MaterialTheme.colorScheme.surface)
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), TILE_SHAPE)
                    .clickable { expanded = !expanded }.padding(8.dp).testTag("$tagPrefix-line"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (chosen != null) IconBadge(chosen.icon, chosen.color, size = 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(chosen?.shownTitle() ?: "Choose an account", style = MaterialTheme.typography.bodyLarge)
                    if (chosen != null) Text(balances[chosen.uid]?.takeIf { !chosen.ignoreInBalance }?.let { Money.format(it, chosen.currencyCode) } ?: chosen.currencyCode, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.ExpandMore, if (expanded) "Hide accounts" else "Show all accounts", Modifier.rotate(turn))
            }
            AnimatedVisibility(expanded, enter = expandVertically(tween(400)) + fadeIn(tween(250)), exit = shrinkVertically(tween(300)) + fadeOut(tween(200))) {
                AccountStrip(accounts, balances, selected, tagPrefix) { onPick(it); expanded = false }
            }
        }
    } else {
        val hidden = accounts.filter { it.ignoreInBalance }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            accounts.forEach { account ->
                val shown = !account.ignoreInBalance || expanded || account.uid == selected
                AnimatedVisibility(shown, enter = expandHorizontally(tween(350)) + fadeIn(tween(250)), exit = shrinkHorizontally(tween(300)) + fadeOut(tween(200))) {
                    Box(Modifier.padding(end = 10.dp)) { AccountTile(account, balances[account.uid], account.uid == selected, "$tagPrefix-${account.uid}") { onPick(account.uid) } }
                }
            }
            if (hidden.isNotEmpty()) Column(
                Modifier.width(88.dp).clip(TILE_SHAPE).border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), TILE_SHAPE)
                    .clickable { expanded = !expanded }.padding(12.dp).testTag("$tagPrefix-hidden-toggle"),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(Icons.Outlined.VisibilityOff, null, Modifier.size(40.dp).padding(8.dp))
                Text(if (expanded) "Hide" else "Hidden", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelLarge)
                Text("${hidden.size} accounts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
