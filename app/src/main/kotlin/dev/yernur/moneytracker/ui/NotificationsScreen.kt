package dev.yernur.moneytracker.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yernur.moneytracker.LocalAppContainer
import dev.yernur.moneytracker.data.Bill
import dev.yernur.moneytracker.data.DEFAULT_CURRENCY
import dev.yernur.moneytracker.data.Money
import dev.yernur.moneytracker.data.ReminderWorker
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val DAILY_TIMES = listOf(9 * 60, 12 * 60, 19 * 60, 20 * 60, 21 * 60, 22 * 60)
private val WARN_PERCENTS = listOf(75, 80, 90, 100)
private val nextFormat = DateTimeFormatter.ofPattern("MMM d")
private val endFormat = DateTimeFormatter.ofPattern("MMM yyyy")

/** How many notifications are on: the daily reminder, limit warnings and each bill reminder not paused (the More row's badge). */
@Composable
fun notificationsOnCount(): Int {
    val container = LocalAppContainer.current
    val daily by container.settings.reminderEnabled.collectAsStateWithLifecycle()
    val warnings by container.settings.notifications.collectAsStateWithLifecycle()
    val bills by remember { container.bills.observe() }.collectAsStateWithLifecycle(emptyList())
    return (if (daily) 1 else 0) + (if (warnings) 1 else 0) + bills.count { it.reminderOn && !it.reminderPaused }
}

/**
 * Every notification the app can send, in one row style: Every day (the daily reminder), Spending (limit warnings), Bills (each bill reminder).
 * A tap on a row opens its choices under it; the switch turns it on or off (pauses a bill reminder); the bin deletes a bill reminder, with
 * Undo. Design: the "Notifications" mock-up. [onOpenRecurring] goes to Analysis -> Recurring, where bill reminders are set.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NotificationsScreen(onOpenRecurring: () -> Unit, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val settings = container.settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val dailyOn by settings.reminderEnabled.collectAsStateWithLifecycle()
    val dailyMinute by settings.reminderMinute.collectAsStateWithLifecycle()
    val warnOn by settings.notifications.collectAsStateWithLifecycle()
    val warnPercent by settings.warnThreshold.collectAsStateWithLifecycle()
    val bills by remember { container.bills.observe() }.collectAsStateWithLifecycle(emptyList())
    var open by remember { mutableStateOf<String?>(null) }
    fun granted() = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var allowed by remember { mutableStateOf(granted()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allowed = granted() } // back from Android's settings
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    fun ask() { if (!granted()) permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
    fun toggle(key: String) { open = if (open == key) null else key }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            AppTopBar(
                title = { Text("Notifications") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp).testTag("notifications"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!allowed) Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(16.dp)).padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp).testTag("notifications-blocked"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Outlined.NotificationsOff, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Text("Android blocks notifications for this app, so none of these can show.", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                TextButton({
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Open settings") }
            }

            GroupTitle("Every day")
            AppCard(Modifier.fillMaxWidth()) {
                NotificationRow(
                    Icons.Outlined.EditNote, null, "Daily reminder",
                    if (dailyOn) "Every day at ${minuteText(dailyMinute)} · \"Add today's expenses\"" else "Off",
                    dailyOn, onToggle = { on -> settings.setReminder(on, dailyMinute); ReminderWorker.schedule(context, settings, replace = true); if (on) ask() },
                    onClick = { toggle("daily") }, tag = "daily",
                )
                if (open == "daily") Choices {
                    ChoiceRow("At", (DAILY_TIMES + dailyMinute).distinct().sorted().map { it to minuteText(it) }, dailyMinute) { m ->
                        settings.setReminder(dailyOn, m); ReminderWorker.schedule(context, settings, replace = true)
                    }
                }
            }

            GroupTitle("Spending")
            AppCard(Modifier.fillMaxWidth()) {
                NotificationRow(
                    Icons.Outlined.Speed, null, "Limit warnings",
                    if (warnOn) "When a category reaches $warnPercent% of its limit, or is on track to" else "Off",
                    warnOn, onToggle = { on -> settings.setNotifications(on); if (on) ask() }, onClick = { toggle("warn") }, tag = "warnings",
                )
                if (open == "warn") Choices {
                    ChoiceRow("Warn at", (WARN_PERCENTS + warnPercent).distinct().sorted().map { it to "$it%" }, warnPercent) { settings.setWarnThreshold(it) }
                    Text("A category can set its own on its screen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            val withReminder = bills.filter { it.reminderOn }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { GroupTitle("Bills · ${withReminder.size}") }
                TextButton(onOpenRecurring, Modifier.testTag("notifications-add-bill")) { Text("Add from Recurring") }
            }
            AppCard(Modifier.fillMaxWidth()) {
                if (withReminder.isEmpty()) Text(
                    "No bill reminders. Set one from Analysis → Recurring.", Modifier.padding(16.dp).testTag("bill-reminders-empty"),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                withReminder.forEachIndexed { i, bill ->
                    if (i > 0) HorizontalDivider()
                    BillRow(
                        bill, onClick = { toggle(bill.key) },
                        onToggle = { on -> scope.launch { container.bills.setReminderPaused(bill, !on) }; if (on) ask() },
                        onDelete = {
                            val before = bill.settings ?: return@BillRow
                            open = null
                            scope.launch {
                                container.bills.removeReminder(bill)
                                val result = snackbar.showSnackbar("Deleted the ${bill.item.title} reminder", actionLabel = "Undo", duration = SnackbarDuration.Short)
                                if (result == SnackbarResult.ActionPerformed) container.bills.restore(before)
                            }
                        },
                    )
                    if (open == bill.key) {
                        val s = bill.settings!!
                        Choices {
                            ChoiceRow("Remind me", REMIND_BEFORE, s.reminderDaysBefore) { d -> scope.launch { container.bills.saveReminder(bill, d, s.reminderMinute) } }
                            ChoiceRow("At", (REMIND_AT + s.reminderMinute).distinct().sorted().map { it to minuteText(it) }, s.reminderMinute) { m ->
                                scope.launch { container.bills.saveReminder(bill, s.reminderDaysBefore, m) }
                            }
                        }
                    }
                }
            }
            Text("Tap a row to change it. The switch pauses; the bin deletes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

@Composable
private fun GroupTitle(text: String) = Text(
    text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 6.dp, top = 8.dp),
)

/** The one row style of this screen: an icon tile, what it is, when it goes off, then any extra buttons and the switch. */
@Composable
private fun NotificationRow(
    icon: ImageVector?, tile: (@Composable () -> Unit)?, title: String, subtitle: String, on: Boolean,
    onToggle: (Boolean) -> Unit, onClick: () -> Unit, tag: String, extra: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp).testTag("notification-$tag"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.alpha(if (on) 1f else 0.45f)) {
            if (tile != null) tile() else Box(
                Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center,
            ) { Icon(icon!!, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        }
        Column(Modifier.weight(1f).alpha(if (on) 1f else 0.55f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        extra()
        Switch(on, onToggle, Modifier.testTag("notification-switch-$tag"))
    }
}

@Composable
private fun BillRow(bill: Bill, onClick: () -> Unit, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    val s = bill.settings!!
    val next = bill.reminderAt
    NotificationRow(
        null, { IconBadge(bill.category?.icon, bill.category?.color, size = 40.dp) },
        "${bill.item.title} · ${Money.formatRounded(bill.item.amount, DEFAULT_CURRENCY)}",
        (if (next != null) "Next ${next.format(nextFormat)}, ${minuteText(s.reminderMinute)}" else "Paused") +
            " · ${remindBeforeText(s.reminderDaysBefore).lowercase()}" + (bill.endMonth?.let { " · ends ${it.format(endFormat)}" } ?: ""),
        !bill.reminderPaused, onToggle, onClick, tag = bill.item.title,
        extra = { IconButton(onDelete, Modifier.testTag("notification-delete-${bill.item.title}")) { Icon(Icons.Outlined.Delete, "Delete the ${bill.item.title} reminder") } },
    )
}

/** The choices under an opened row, lined up with its text. */
@Composable
private fun Choices(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 66.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(label: String, options: List<Pair<Int, String>>, current: Int, onPick: (Int) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, text) -> FilterChip(value == current, { onPick(value) }, { Text(text) }, Modifier.testTag("choice-$label-$value")) }
    }
}
