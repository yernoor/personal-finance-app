package dev.yernur.moneytracker.ui

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.AccentPalette
import dev.yernur.moneytracker.data.IconStyle
import dev.yernur.moneytracker.data.PeriodUnit
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import dev.yernur.moneytracker.data.importer.MmbackupMapper
import dev.yernur.moneytracker.data.importer.MmbackupReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MoreScreen(onCategories: () -> Unit, onTags: () -> Unit, onNavbar: () -> Unit, onHomeLayout: () -> Unit) {
    var showWarnings by remember { mutableStateOf(false) }
    var showHistoryPeriod by remember { mutableStateOf(false) }
    var showIconStyle by remember { mutableStateOf(false) }
    val container = dev.yernur.moneytracker.LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            message = try {
                val result = withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    MmbackupMapper.map(MmbackupReader.read(bytes, context.cacheDir))
                }
                container.importer.store(result)
                "Imported ${result.transactions.size} transactions, ${result.transfers.size} transfers, " +
                    "${result.accounts.size} accounts, ${result.categories.size} categories." +
                    result.warnings.take(5).joinToString("") { "\n• $it" }
            } catch (e: Exception) {
                "Import failed: ${e.message}"
            }
        }
    }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        ListItem(
            modifier = Modifier.clickable(onClick = onCategories).testTag("open-categories"),
            leadingContent = { Icon(Icons.Outlined.Category, null) }, headlineContent = { Text("Categories") },
        )
        ListItem(
            modifier = Modifier.clickable(onClick = onTags).testTag("open-tags"),
            leadingContent = { Icon(Icons.AutoMirrored.Outlined.Label, null) }, headlineContent = { Text("Tags") },
        )
        ListItem(
            modifier = Modifier.clickable(onClick = onNavbar).testTag("open-navbar-settings"),
            leadingContent = { Icon(Icons.Outlined.Tune, null) }, headlineContent = { Text("Navigation bar") },
            supportingContent = { Text("Choose and order the tabs") },
        )
        ListItem(
            modifier = Modifier.clickable(onClick = onHomeLayout).testTag("open-home-settings"),
            leadingContent = { Icon(Icons.Outlined.Dashboard, null) }, headlineContent = { Text("Home layout") },
            supportingContent = { Text("Choose and order the Home cards") },
        )
        val glass by container.settings.glass.collectAsStateWithLifecycle()
        val iconStyle by container.settings.iconStyle.collectAsStateWithLifecycle()
        ListItem(
            modifier = Modifier.clickable(enabled = !glass) { showIconStyle = true }.testTag("open-icon-style"),
            leadingContent = { Icon(Icons.Outlined.Palette, null) }, headlineContent = { Text("Icon style") },
            supportingContent = { Text(if (glass) "Glass icons are used with liquid glass" else iconStyle.label) },
        )
        ListItem(
            modifier = Modifier.clickable { container.settings.setGlass(!glass) }.testTag("glass-toggle"),
            leadingContent = { Icon(Icons.Outlined.BlurOn, null) }, headlineContent = { Text("Liquid glass (experimental)") },
            supportingContent = { Text("Floating glass bar and add button, frosted sheets and dialogs") },
            trailingContent = { Switch(glass, { container.settings.setGlass(it) }) },
        )
        val historyPeriod by container.settings.historyPeriod.collectAsStateWithLifecycle()
        ListItem(
            modifier = Modifier.clickable { showHistoryPeriod = true }.testTag("open-history-period"),
            leadingContent = { Icon(Icons.Outlined.History, null) }, headlineContent = { Text("History opens on") },
            supportingContent = { Text(historyPeriod.name) },
        )
        ListItem(
            modifier = Modifier.clickable { showWarnings = true }.testTag("open-warnings"),
            leadingContent = { Icon(Icons.Outlined.NotificationsActive, null) }, headlineContent = { Text("Spending warnings") },
            supportingContent = { Text("Threshold and notifications") },
        )
        ListItem(
            modifier = Modifier.clickable { picker.launch(arrayOf("*/*")) }.testTag("import"),
            leadingContent = { Icon(Icons.Outlined.FileUpload, null) }, headlineContent = { Text("Import .mmbackup") },
            supportingContent = { Text("Adds or overwrites by id; nothing local is deleted") },
        )
    }
    if (showIconStyle) IconStyleDialog(container.settings) { showIconStyle = false }
    if (showHistoryPeriod) HistoryPeriodDialog(container.settings) { showHistoryPeriod = false }
    if (showWarnings) WarningSettingsDialog(container.settings) { showWarnings = false }
    message?.let {
        AlertDialog(
            onDismissRequest = { message = null }, containerColor = glassContainer(AlertDialogDefaults.containerColor),
            confirmButton = { TextButton({ message = null }) { Text("OK") } }, text = { GlassWindowBlur(); Text(it) },
        )
    }
}

@Composable
private fun WarningSettingsDialog(settings: AppSettings, onDismiss: () -> Unit) {
    val threshold by settings.warnThreshold.collectAsStateWithLifecycle()
    val notifications by settings.notifications.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf(threshold.toString()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    AlertDialog(
        containerColor = glassContainer(AlertDialogDefaults.containerColor),
        onDismissRequest = onDismiss,
        title = { Text("Spending warnings") },
        text = { GlassWindowBlur(); 
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    text, { text = it.filter(Char::isDigit) }, label = { Text("Warn at % of a category limit") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.testTag("warn-threshold"),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Notifications", Modifier.weight(1f))
                    Switch(notifications, {
                        settings.setNotifications(it)
                        if (it) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    })
                }
            }
        },
        confirmButton = {
            TextButton({ text.toIntOrNull()?.takeIf { it > 0 }?.let(settings::setWarnThreshold); onDismiss() }) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun HistoryPeriodDialog(settings: AppSettings, onDismiss: () -> Unit) {
    val current by settings.historyPeriod.collectAsStateWithLifecycle()
    AlertDialog(
        containerColor = glassContainer(AlertDialogDefaults.containerColor),
        onDismissRequest = onDismiss,
        title = { Text("History opens on") },
        text = { GlassWindowBlur(); 
            Column {
                PeriodUnit.entries.forEach { unit ->
                    Row(
                        Modifier.fillMaxWidth().clickable { settings.setHistoryPeriod(unit); onDismiss() }.testTag("history-default-${unit.name}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(current == unit, { settings.setHistoryPeriod(unit); onDismiss() })
                        Text(unit.name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Close") } },
    )
}

/** Three looks to pick from, each previewed with a few real icons in its own style. */
@Composable
private fun IconStyleDialog(settings: AppSettings, onDismiss: () -> Unit) {
    val current by settings.iconStyle.collectAsStateWithLifecycle()
    val choices = listOf(IconStyle.Vivid, IconStyle.Tonal, IconStyle.Duotone)
    val samples = listOf("cafe" to 8, "transport" to 4, "home" to 6, "products" to 5, "heart" to 1, "present" to 3)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = glassContainer(AlertDialogDefaults.containerColor),
        title = { Text("Icon style") },
        text = {
            GlassWindowBlur()
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                choices.forEach { style ->
                    Column(Modifier.fillMaxWidth().clickable { settings.setIconStyle(style) }.testTag("icon-style-${style.name}")) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(current == style, { settings.setIconStyle(style) })
                            Text(style.label)
                        }
                        CompositionLocalProvider(LocalIconStyle provides style) {
                            Row(Modifier.padding(start = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                samples.forEach { (name, color) -> IconBadge(name, AccentPalette.KEYS[color], size = 36.dp) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Close") } },
    )
}
