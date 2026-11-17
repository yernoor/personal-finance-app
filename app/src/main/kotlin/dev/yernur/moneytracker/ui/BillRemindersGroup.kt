package dev.yernur.moneytracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yernur.moneytracker.LocalAppContainer
import dev.yernur.moneytracker.data.Bill
import dev.yernur.moneytracker.data.DEFAULT_CURRENCY
import dev.yernur.moneytracker.data.Money
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val nextFormat = DateTimeFormatter.ofPattern("MMM d")
private val endFormat = DateTimeFormatter.ofPattern("MMM yyyy")

/**
 * More -> Notifications, under the daily reminder: every bill reminder set on the Recurring page. The switch pauses one without forgetting
 * it; a tap opens the same sheet to change or remove it.
 */
@Composable
fun BillRemindersGroup() {
    val container = LocalAppContainer.current
    val bills by remember { container.bills.observe() }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Bill?>(null) }
    val withReminder = bills.filter { it.reminderOn }
    Text(
        "Bill reminders", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
    if (withReminder.isEmpty()) Text(
        "No bill reminders yet. Set one from Analysis → Recurring.", style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("bill-reminders-empty"),
    )
    withReminder.forEach { bill ->
        val s = bill.settings!!
        val next = bill.reminderAt
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent), // lets the glass glow show through
            modifier = Modifier.clickable { editing = bill }.testTag("bill-reminder-${bill.item.title}"),
            leadingContent = { IconBadge(bill.category?.icon, bill.category?.color, size = 36.dp) },
            headlineContent = { Text("${bill.item.title} · ${Money.formatRounded(bill.item.amount, DEFAULT_CURRENCY)}") },
            supportingContent = {
                Text(
                    (if (next != null) "Next ${next.format(nextFormat)}, ${minuteText(s.reminderMinute)}" else "Paused") +
                        " · ${remindBeforeText(s.reminderDaysBefore).lowercase()}" + (bill.endMonth?.let { " · ends ${it.format(endFormat)}" } ?: ""),
                )
            },
            trailingContent = {
                Switch(!bill.reminderPaused, { on -> scope.launch { container.bills.setReminderPaused(bill, !on) } }, Modifier.testTag("bill-reminder-switch-${bill.item.title}"))
            },
        )
    }
    editing?.let { bill ->
        BillReminderSheet(
            bill, onSave = { before, minute -> scope.launch { container.bills.saveReminder(bill, before, minute) }; editing = null },
            onRemove = { scope.launch { container.bills.removeReminder(bill) }; editing = null }, onDismiss = { editing = null },
        )
    }
}
