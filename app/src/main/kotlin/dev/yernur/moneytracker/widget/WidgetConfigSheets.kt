package dev.yernur.moneytracker.widget

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.yernur.moneytracker.ui.Semantic
import kotlinx.coroutines.launch

/** Widget settings drawn as a bottom sheet in the app's own style (the system list dialog looked like Android 4). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigSheet(title: String, subtitle: String, onCancel: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(onDismissRequest = onCancel, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

private fun QuickAddKind.icon(): ImageVector = when (this) {
    QuickAddKind.Expense -> Icons.Outlined.ArrowDownward
    QuickAddKind.Income -> Icons.Outlined.ArrowUpward
    QuickAddKind.Transfer -> Icons.Outlined.SwapHoriz
}

/** Expense and income keep their fixed colors; a transfer takes the accent. */
@Composable
fun QuickAddKind.tint(): Color = when (this) {
    QuickAddKind.Expense -> Semantic.expense
    QuickAddKind.Income -> Semantic.income
    QuickAddKind.Transfer -> MaterialTheme.colorScheme.primary
}

@Composable
fun QuickAddConfigSheet(initial: QuickAddKind, onCancel: () -> Unit, onPick: (QuickAddKind) -> Unit) {
    var picked by remember { mutableStateOf(initial) }
    ConfigSheet("Quick add opens", "Choose what this widget adds", onCancel) {
        QuickAddKind.entries.forEach { kind ->
            val selected = kind == picked
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .clickable { picked = kind }.padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(kind.tint().copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(kind.icon(), null, Modifier.size(20.dp), tint = kind.tint())
                }
                Text(kind.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                if (selected) Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(picked.button, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onCancel, Modifier.weight(1f)) { Text("Cancel") }
            Button({ onPick(picked) }, Modifier.weight(1f)) { Text("Place widget") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RatesConfigSheet(choices: List<String>, rates: Map<String, Double>, initial: Set<String>, onCancel: () -> Unit, onPick: (List<String>) -> Unit) {
    var picked by remember { mutableStateOf(choices.filter { it in initial }) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    ConfigSheet("Currency rates", "Up to $MAX_RATE_CURRENCIES currencies · ${picked.size} chosen", onCancel) {
        Row(Modifier.fillMaxWidth().graphicsLayer { translationX = shake.value }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(MAX_RATE_CURRENCIES) { slot ->
                val code = picked.getOrNull(slot)
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (code != null) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(code ?: "Empty", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(code?.let { rates[it] }?.let { "%.2f".format(it) } ?: "-", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        FlowRow(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { code ->
                FilterChip(code in picked, {
                    when {
                        code in picked -> picked = picked - code
                        picked.size >= MAX_RATE_CURRENCIES -> scope.launch { // a fourth pick shakes the slots instead of adding
                            for (x in listOf(-14f, 12f, -8f, 5f, 0f)) shake.animateTo(x, tween(45))
                        }
                        else -> picked = picked + code
                    }
                }, label = { Text(code) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onCancel, Modifier.weight(1f)) { Text("Cancel") }
            Button({ onPick(picked) }, Modifier.weight(1f), enabled = picked.isNotEmpty()) { Text("Place widget") }
        }
    }
}
