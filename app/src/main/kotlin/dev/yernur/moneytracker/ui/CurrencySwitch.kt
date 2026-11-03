package dev.yernur.moneytracker.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size

/** The button that switches the currency a screen shows; [choices] come from the cached National Bank rates. */
@Composable
fun CurrencySwitch(current: String, choices: List<String>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    AssistChip(
        onClick = { open = true }, modifier = modifier.testTag("currency-switch"),
        label = { Text(current) }, leadingIcon = { Icon(Icons.Outlined.CurrencyExchange, null, Modifier.size(18.dp)) },
    )
    DropdownMenu(open, { open = false }) {
        choices.forEach { code ->
            DropdownMenuItem(text = { Text(code) }, modifier = Modifier.testTag("currency-$code"), onClick = { open = false; onPick(code) })
        }
    }
}
