package dev.yernur.moneytracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.yernur.moneytracker.data.Bill
import dev.yernur.moneytracker.data.DEFAULT_CURRENCY
import dev.yernur.moneytracker.data.Money
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** How early a bill reminder goes off, and at what time (minutes after midnight): the choices the sheet offers. */
private val REMIND_BEFORE = listOf(0 to "On the day", 1 to "1 day before", 3 to "3 days before")
private val REMIND_AT = listOf(9 * 60, 10 * 60, 12 * 60, 19 * 60)

private val dayFormat = DateTimeFormatter.ofPattern("MMM d")
fun minuteText(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)
fun remindBeforeText(days: Int) = REMIND_BEFORE.firstOrNull { it.first == days }?.second ?: "$days days before"

/** Sets, changes or removes a bill's reminder: how early, what time, and a preview of the next one. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BillReminderSheet(bill: Bill, onSave: (daysBefore: Int, minute: Int) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    var before by remember { mutableIntStateOf(bill.settings?.reminderDaysBefore ?: 1) }
    var minute by remember { mutableIntStateOf(bill.settings?.reminderMinute ?: 10 * 60) }
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = glassContainer(BottomSheetDefaults.ContainerColor)) {
        GlassWindowBlur()
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).testTag("reminder-sheet"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Remind me about ${bill.item.title}", style = MaterialTheme.typography.titleLarge)
            Text(
                "Every month, around day ${bill.item.next.dayOfMonth} (from your last payment) · ${Money.formatRounded(bill.item.amount, DEFAULT_CURRENCY)}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Remind me", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                REMIND_BEFORE.forEach { (days, label) -> FilterChip(before == days, { before = days }, { Text(label) }, Modifier.testTag("remind-before-$days")) }
            }
            Text("At", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                REMIND_AT.forEach { m -> FilterChip(minute == m, { minute = m }, { Text(minuteText(m)) }, Modifier.testTag("remind-at-$m")) }
            }
            val firesOn = bill.item.next.minusDays(before.toLong())
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.NotificationsNone, null, Modifier.size(20.dp))
                    Text("Next: ${firesOn.format(dayFormat)}, ${minuteText(minute)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (bill.settings?.reminderOn == true) OutlinedButton(onRemove, Modifier.weight(1f).height(48.dp).testTag("reminder-remove")) { Text("Remove") }
                Button({ onSave(before, minute) }, Modifier.weight(1f).height(48.dp).testTag("reminder-save")) { Text("Save reminder") }
            }
        }
    }
}

/** Sets when an installment-like bill ends: how many payments are left, counting the next one; or that it is ongoing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillEndSheet(bill: Bill, onSave: (paymentsLeft: Int) -> Unit, onOngoing: () -> Unit, onDismiss: () -> Unit) {
    val start = bill.endMonth?.let { (java.time.temporal.ChronoUnit.MONTHS.between(YearMonth.from(bill.item.next), it).toInt() + 1).coerceAtLeast(1) } ?: 6
    var left by remember { mutableIntStateOf(start) }
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = glassContainer(BottomSheetDefaults.ContainerColor)) {
        GlassWindowBlur()
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).testTag("end-sheet"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("When does ${bill.item.title} end?", style = MaterialTheme.typography.titleLarge)
            Text(
                "${Money.formatRounded(bill.item.amount, DEFAULT_CURRENCY)} a month · ${bill.item.months} payments seen so far",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Payments left, counting the next one", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedIconButton({ left = (left - 1).coerceAtLeast(1) }, Modifier.size(48.dp).testTag("end-minus")) { Text("−", style = MaterialTheme.typography.headlineSmall) }
                Text(left.toString(), Modifier.widthIn(min = 48.dp).testTag("end-left"), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                OutlinedIconButton({ left = (left + 1).coerceAtMost(120) }, Modifier.size(48.dp).testTag("end-plus")) { Text("+", style = MaterialTheme.typography.headlineSmall) }
            }
            val last = bill.item.next.plusMonths(left - 1L)
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Flag, null, Modifier.size(20.dp))
                    Text(
                        "Last payment ${last.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))} · ${Money.formatRounded(bill.item.amount * left, DEFAULT_CURRENCY)} still to pay",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (bill.endMonth != null) OutlinedButton(onOngoing, Modifier.weight(1f).height(48.dp).testTag("end-ongoing")) { Text("It's ongoing") }
                Button({ onSave(left) }, Modifier.weight(1f).height(48.dp).testTag("end-save")) { Text("Save") }
            }
        }
    }
}
