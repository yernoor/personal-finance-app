package dev.yernur.moneytracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.yernur.moneytracker.data.Money
import dev.yernur.moneytracker.data.db.BondEntity
import dev.yernur.moneytracker.data.db.CashflowItemEntity
import dev.yernur.moneytracker.data.db.CashflowKind
import dev.yernur.moneytracker.data.db.ManualKind
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException

sealed interface FormField { val label: String }

/** [numeric] only picks the keyboard. */
data class TextFormField(override val label: String, val initial: String = "", val numeric: Boolean = false) : FormField

data class ChoiceFormField(override val label: String, val options: List<String>, val initial: Int = 0) : FormField

/** A small form in a dialog. The values come back as text, one per field in order (a choice gives its option's text). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FormDialog(
    title: String, fields: List<FormField>, valid: (List<String>) -> Boolean,
    onDismiss: () -> Unit, onDelete: (() -> Unit)? = null, onConfirm: (List<String>) -> Unit,
) {
    val values = remember {
        mutableStateListOf<String>().apply { addAll(fields.map { f -> if (f is TextFormField) f.initial else (f as ChoiceFormField).options[f.initial] }) }
    }
    AlertDialog(
        containerColor = glassContainer(AlertDialogDefaults.containerColor),
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            GlassWindowBlur()
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fields.forEachIndexed { i, field ->
                    when (field) {
                        is TextFormField -> OutlinedTextField(
                            values[i], { values[i] = it }, Modifier.testTag("form-$i"), label = { Text(field.label) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = if (field.numeric) KeyboardType.Decimal else KeyboardType.Text),
                        )
                        is ChoiceFormField -> Column {
                            Text(field.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                field.options.forEachIndexed { j, option ->
                                    FilterChip(values[i] == option, { values[i] = option }, { Text(option) }, Modifier.testTag("form-$i-$j"))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton({ onConfirm(values.toList()) }, enabled = valid(values.toList()), modifier = Modifier.testTag("form-save")) { Text("Save") } },
        dismissButton = {
            Column {
                TextButton(onDismiss) { Text("Cancel") }
                if (onDelete != null) TextButton(onDelete, modifier = Modifier.testTag("form-delete")) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        },
    )
}

// Parsers: the dialog's text values -> what the repository stores. Null = not valid yet (the Save button stays off).

private fun positive(text: String): Long? = Money.parseMinor(text)?.takeIf { it > 0 }

private fun dateOrNull(text: String): LocalDate? = try { LocalDate.parse(text.trim()) } catch (e: DateTimeParseException) { null }

/** Fields: title, kind, currency, value. */
data class AssetDraft(val title: String, val kind: ManualKind, val currency: String, val valueMinor: Long)

fun parseAsset(v: List<String>): AssetDraft? {
    val title = v[0].trim().ifEmpty { return null }
    return AssetDraft(title, if (v[1] == "Loan or debt") ManualKind.Liability else ManualKind.Asset, v[2], positive(v[3]) ?: return null)
}

/** Fields: title, target (in the shown currency), deadline (blank = none), measured against. [accountTitle] null = net worth. */
data class GoalDraft(val title: String, val targetMinor: Long, val deadline: LocalDate?, val accountTitle: String?)

fun parseGoal(v: List<String>, netWorthChoice: String): GoalDraft? {
    val title = v[0].trim().ifEmpty { return null }
    val deadline = v[2].trim().takeIf { it.isNotEmpty() }?.let { dateOrNull(it) ?: return null }
    return GoalDraft(title, positive(v[1]) ?: return null, deadline, v[3].takeIf { it != netWorthChoice })
}

/** Fields: title, currency, face value of one piece, pieces, total paid, coupon % a year, coupons a year, maturity, current value (optional). */
fun parseBond(v: List<String>): BondEntity? {
    val title = v[0].trim().ifEmpty { return null }
    val rate = v[5].trim().replace(',', '.').toBigDecimalOrNull()?.takeIf { it.signum() >= 0 && it <= BigDecimal(100) } ?: return null
    val pieces = v[3].trim().toIntOrNull()?.takeIf { it > 0 } ?: return null
    val maturity = dateOrNull(v[7]) ?: return null
    val current = if (v[8].isBlank()) null else positive(v[8]) ?: return null
    return BondEntity(
        "", title, v[1], positive(v[2]) ?: return null, pieces, positive(v[4]) ?: return null,
        rate.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toInt(), v[6].toInt(), maturity.toString(), current, "", "",
    )
}

/** Fields: title, kind, monthly amount, currency, already logged as expenses. */
fun parseCashflow(v: List<String>): CashflowItemEntity? {
    val title = v[0].trim().ifEmpty { return null }
    val kind = if (v[1] == "Income") CashflowKind.Income else CashflowKind.Obligation
    return CashflowItemEntity("", title, kind, v[3], positive(v[2]) ?: return null, v[4] == "Yes", "", "")
}
