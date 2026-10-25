package dev.yernur.moneytracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.yernur.moneytracker.data.Calculator
import dev.yernur.moneytracker.data.Money

/** Key rows like the phone's calculator; "()" is the smart parenthesis, "DEL" the backspace. */
private val KEY_ROWS = listOf(
    listOf("C", "()", "%", "÷"),
    listOf("7", "8", "9", "×"),
    listOf("4", "5", "6", "−"),
    listOf("1", "2", "3", "+"),
    listOf("0", ".", "DEL", "="),
)
private val OPERATOR_KEYS = setOf("÷", "×", "−", "+", "%", "()", "C")

/**
 * Bottom-sheet calculator. Starts from the amount already typed; "Use result" hands back a plain number
 * (rounded to cents by the form's own parser).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorSheet(initial: String, onUse: (String) -> Unit, onDismiss: () -> Unit) {
    var expression by remember { mutableStateOf(initial.trim().replace(',', '.')) }
    val result = Calculator.evaluate(expression)
    // Show a live preview only when it adds something over what is already on the display.
    val preview = result?.let(Calculator::format)?.takeIf { it != expression }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                expression.ifEmpty { "0" }, Modifier.fillMaxWidth().testTag("calc-expression"), textAlign = TextAlign.End,
                style = MaterialTheme.typography.displaySmall, maxLines = 2,
            )
            Text(
                preview?.let { "= $it" }.orEmpty(), Modifier.fillMaxWidth().height(28.dp).testTag("calc-preview"), textAlign = TextAlign.End,
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            KEY_ROWS.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { key ->
                        val modifier = Modifier.weight(1f).height(60.dp).testTag("calc-$key")
                        val press = { expression = Calculator.press(expression, key) }
                        when {
                            key == "=" -> Button(press, modifier) { Text("=", style = MaterialTheme.typography.titleLarge) }
                            key == "DEL" -> FilledTonalButton(press, modifier) { Icon(Icons.AutoMirrored.Outlined.Backspace, "Delete") }
                            key in OPERATOR_KEYS -> FilledTonalButton(
                                press, modifier, colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            ) { Text(key, style = MaterialTheme.typography.titleLarge) }
                            else -> FilledTonalButton(
                                press, modifier, colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                            ) { Text(key, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface) }
                        }
                    }
                }
            }
            Button(
                { result?.let { onUse(Money.editable(Money.parseMinor(it.toPlainString()) ?: 0L)) } },
                Modifier.fillMaxWidth().testTag("calc-use"), enabled = result != null,
            ) { Text("Use result") }
        }
    }
}
