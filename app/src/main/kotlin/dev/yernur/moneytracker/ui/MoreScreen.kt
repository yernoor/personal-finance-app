package dev.yernur.moneytracker.ui

import dev.yernur.moneytracker.data.AnimationKind
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import dev.yernur.moneytracker.data.ReminderWorker
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.AccentPalette
import dev.yernur.moneytracker.data.WidgetStyle
import dev.yernur.moneytracker.data.IconStyle
import dev.yernur.moneytracker.data.PeriodUnit
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import dev.yernur.moneytracker.data.backup.BackupException
import dev.yernur.moneytracker.data.backup.BackupInfo
import dev.yernur.moneytracker.data.importer.MmbackupMapper
import dev.yernur.moneytracker.data.security.RecentsMode
import dev.yernur.moneytracker.data.importer.MmbackupReader
import dev.yernur.moneytracker.data.update.isNewer
import dev.yernur.moneytracker.data.update.parseVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MoreSections : androidx.lifecycle.ViewModel() {
    var open by mutableStateOf<String?>(null)
}

@Composable
fun MoreScreen(onCategories: () -> Unit, onTags: () -> Unit, onNavbar: () -> Unit, onHomeLayout: () -> Unit, onSync: () -> Unit, onUpdates: () -> Unit, onFeedback: () -> Unit, onCompound: () -> Unit, onWealth: () -> Unit, onAfford: () -> Unit, onOpenTab: (NavTab) -> Unit) {
    var showReminder by remember { mutableStateOf(false) }
    var showWalletConsent by remember { mutableStateOf(false) }
    var showWalletAccount by remember { mutableStateOf(false) }
    var showWarnings by remember { mutableStateOf(false) }
    var showHistoryPeriod by remember { mutableStateOf(false) }
    var showIconStyle by remember { mutableStateOf(false) }
    var showWidgetStyle by remember { mutableStateOf(false) }
    var pinDialog by remember { mutableStateOf<PinDialog?>(null) }
    var showAutoLock by remember { mutableStateOf(false) }
    var showRecents by remember { mutableStateOf(false) }
    val container = dev.yernur.moneytracker.LocalAppContainer.current
    val glass by container.settings.glass.collectAsStateWithLifecycle()
    val navbar by container.settings.navTabs.collectAsStateWithLifecycle() // a screen already in the navbar is not listed here again
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

    val backups = container.backups
    var staged by remember { mutableStateOf<Pair<java.io.File, BackupInfo>?>(null) }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if (uri == null || backups == null) return@rememberLauncherForActivityResult
        scope.launch {
            message = try {
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.use { backups.write(it) } }
                "Backup saved."
            } catch (e: Exception) {
                "Backup failed: ${e.message}"
            }
        }
    }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null || backups == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                staged = withContext(Dispatchers.IO) { backups.stage(context.contentResolver.openInputStream(uri)!!) }
            } catch (e: Exception) {
                message = if (e is BackupException) e.message else "Could not read that file: ${e.message}"
            }
        }
    }

    LaunchedEffect(Unit) {
        container.checkForUpdateDaily()
        container.refreshRatesDaily()
        container.snapshotNetWorth()
    }
    // One group open at a time keeps the page short. Held in a ViewModel: switching liquid glass rebuilds the screen, and plain state would close the group.
    val sections = appViewModel { MoreSections() }
    var openId by sections::open
    Column(Modifier.verticalScroll(rememberScrollState())) {
        val hidden = NavTab.entries.filter { it.id !in navbar }
        val tools = hidden.filter { it.id !in setOf("wealth", "categories", "tags") } // those three have their own rows below
        if (tools.isNotEmpty()) {
            SectionHeader("Screens")
            tools.forEach { tab ->
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent), // lets the glass glow show through
                    modifier = Modifier.clickable { onOpenTab(tab) }.testTag("open-${tab.id}"),
                    leadingContent = { Icon(tab.icon, null) }, headlineContent = { Text(tab.label) },
                )
            }
        }
        SectionHeader("Wealth")
        if ("wealth" !in navbar) ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent), // lets the glass glow show through
            modifier = Modifier.clickable(onClick = onWealth).testTag("open-wealth"),
            leadingContent = { Icon(Icons.AutoMirrored.Outlined.TrendingUp, null) }, headlineContent = { Text("Wealth") },
            supportingContent = { Text("Net worth, goals, bonds, monthly money") },
        )
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent), // lets the glass glow show through
            modifier = Modifier.clickable(onClick = onCompound).testTag("open-compound"),
            leadingContent = { Icon(Icons.Outlined.Calculate, null) }, headlineContent = { Text("Compound interest") },
            supportingContent = { Text("Where a balance and monthly savings end up") },
        )
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent), // lets the glass glow show through
            modifier = Modifier.clickable(onClick = onAfford).testTag("open-afford"),
            leadingContent = { Icon(Icons.Outlined.ShoppingCart, null) }, headlineContent = { Text("Can I afford this?") },
            supportingContent = { Text("Check a purchase against your balance, limits and goals") },
        )
        if ("categories" !in navbar || "tags" !in navbar) SectionHeader("Manage")
        if ("categories" !in navbar) ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent), // lets the glass glow show through
            modifier = Modifier.clickable(onClick = onCategories).testTag("open-categories"),
            leadingContent = { Icon(Icons.Outlined.Category, null) }, headlineContent = { Text("Categories") },
        )
        if ("tags" !in navbar) ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent), // lets the glass glow show through
            modifier = Modifier.clickable(onClick = onTags).testTag("open-tags"),
            leadingContent = { Icon(Icons.AutoMirrored.Outlined.Label, null) }, headlineContent = { Text("Tags") },
        )
        Section("appearance", "Appearance", openId, { openId = it }) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent), // lets the glass glow show through
                modifier = Modifier.clickable(onClick = onNavbar).testTag("open-navbar-settings"),
                leadingContent = { Icon(Icons.Outlined.Tune, null) }, headlineContent = { Text("Navigation bar") },
                supportingContent = { Text("Choose and order the tabs") },
            )
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent), // lets the glass glow show through
                modifier = Modifier.clickable(onClick = onHomeLayout).testTag("open-home-settings"),
                leadingContent = { Icon(Icons.Outlined.Dashboard, null) }, headlineContent = { Text("Home layout") },
                supportingContent = { Text("Choose and order the Home cards") },
            )
            val iconStyle by container.settings.iconStyle.collectAsStateWithLifecycle()
            val glassIconStyle by container.settings.glassIconStyle.collectAsStateWithLifecycle()
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent), // lets the glass glow show through
                modifier = Modifier.clickable { showIconStyle = true }.testTag("open-icon-style"),
                leadingContent = { Icon(Icons.Outlined.Palette, null) }, headlineContent = { Text("Icon style") },
                // Liquid glass has its own two looks; the everyday ones are not offered while it is on
                supportingContent = { Text(if (glass) "${glassIconStyle.label} (liquid glass)" else iconStyle.label) },
            )
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent), // lets the glass glow show through
                modifier = Modifier.clickable { container.settings.setGlass(!glass) }.testTag("glass-toggle"),
                leadingContent = { Icon(Icons.Outlined.BlurOn, null) }, headlineContent = { Text("Liquid glass (experimental)") },
                supportingContent = { Text("Floating glass bar and add button, frosted sheets and dialogs") },
                trailingContent = { Switch(glass, { container.settings.setGlass(it) }) },
            )
            val widgetStyle by container.settings.widgetStyle.collectAsStateWithLifecycle()
            fun refreshWidgets() {
                scope.launch {
                    dev.yernur.moneytracker.widget.updateAllWidgets(context.applicationContext)
                    dev.yernur.moneytracker.widget.updateWidgetPreviews(context.applicationContext)
                }
            }
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { showWidgetStyle = true }.testTag("widget-style"),
                leadingContent = { Icon(Icons.Outlined.Widgets, null) }, headlineContent = { Text("Widget style") },
                supportingContent = { Text("${widgetStyle.label}: ${widgetStyle.description}. Widgets show spending, never account balances") },
            )
        }
        Section("animations", "Animations", openId, { openId = it }) {
            val off by container.settings.animationsOff.collectAsStateWithLifecycle()
            AnimationKind.entries.forEach { kind ->
                val on = kind !in off
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent), // lets the glass glow show through
                    modifier = Modifier.clickable { container.settings.setAnimation(kind, !on) }.testTag("animation-${kind.id}"),
                    headlineContent = { Text(kind.label) }, supportingContent = { Text(kind.description) },
                    trailingContent = { Switch(on, { container.settings.setAnimation(kind, it) }) },
                )
            }
        }
        Section("homehistory", "Home and History", openId, { openId = it }) {
            val showAll by container.settings.showAllAccounts.collectAsStateWithLifecycle()
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent), // lets the glass glow show through
                modifier = Modifier.clickable { container.settings.setShowAllAccounts(!showAll) }.testTag("toggle-all-accounts"),
                leadingContent = { Icon(Icons.Outlined.Tune, null) }, headlineContent = { Text("Show \"All accounts\" on Home") },
                trailingContent = { Switch(showAll, { container.settings.setShowAllAccounts(it) }) },
            )
            val historyPeriod by container.settings.historyPeriod.collectAsStateWithLifecycle()
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent), // lets the glass glow show through
                modifier = Modifier.clickable { showHistoryPeriod = true }.testTag("open-history-period"),
                leadingContent = { Icon(Icons.Outlined.History, null) }, headlineContent = { Text("History opens on") },
                supportingContent = { Text(historyPeriod.name) },
            )
        }
        Section("notifications", "Notifications", openId, { openId = it }) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent), // lets the glass glow show through
                modifier = Modifier.clickable { showWarnings = true }.testTag("open-warnings"),
                leadingContent = { Icon(Icons.Outlined.NotificationsActive, null) }, headlineContent = { Text("Spending warnings") },
                supportingContent = { Text("Threshold and notifications") },
            )
            val reminderOn by container.settings.reminderEnabled.collectAsStateWithLifecycle()
            val reminderMinute by container.settings.reminderMinute.collectAsStateWithLifecycle()
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent), // lets the glass glow show through
                modifier = Modifier.clickable { showReminder = true }.testTag("open-reminder"),
                leadingContent = { Icon(Icons.Outlined.NotificationsActive, null) }, headlineContent = { Text("Daily reminder") },
                supportingContent = {
                    val blocked = reminderOn && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    Text(
                        if (!reminderOn) "Off" else "Every day at %02d:%02d".format(reminderMinute / 60, reminderMinute % 60) +
                            if (blocked) " (notifications are blocked for this app in Android settings)" else "",
                    )
                },
            )
        }
        Section("automation", "Automation", openId, { openId = it }) {
            val walletOn by container.settings.walletCapture.collectAsStateWithLifecycle()
            val walletAccountUid by container.settings.walletAccountUid.collectAsStateWithLifecycle()
            val accounts by remember { container.db.accountDao().observeLive() }.collectAsStateWithLifecycle(emptyList())
            val walletAccessGranted = {
                context.getSystemService(android.app.NotificationManager::class.java)
                    .isNotificationListenerAccessGranted(android.content.ComponentName(context, dev.yernur.moneytracker.data.wallet.WalletListenerService::class.java))
            }
            var walletAccess by remember { mutableStateOf(walletAccessGranted()) }
            androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { walletAccess = walletAccessGranted() } // back from Android's settings
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { if (walletOn) container.settings.setWalletCapture(false) else showWalletConsent = true }.testTag("wallet-capture"),
                leadingContent = { Icon(Icons.Outlined.AccountBalanceWallet, null) }, headlineContent = { Text("Google Wallet payments") },
                supportingContent = {
                    Text(
                        when {
                            !walletOn -> "Off. Offer each Wallet card payment as an expense"
                            !walletAccess -> "On, but Android notification access is still off (see the next row)"
                            else -> "On: each card payment is offered as an expense"
                        },
                    )
                },
                trailingContent = { Switch(walletOn, { if (it) showWalletConsent = true else container.settings.setWalletCapture(false) }) },
            )
            if (walletOn && !walletAccess) ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.testTag("wallet-access"),
                leadingContent = { Icon(Icons.Outlined.AccountBalanceWallet, null) }, headlineContent = { Text("Allow notification access") },
                supportingContent = { Text("Opens Android's screen: switch Money Tracker on") },
            )
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { showWalletAccount = true }.testTag("wallet-account"),
                leadingContent = { Icon(Icons.Outlined.AccountBalanceWallet, null) }, headlineContent = { Text("Default account for payments") },
                supportingContent = { Text(accounts.firstOrNull { it.uid == walletAccountUid }?.title ?: "Not chosen: the first payment asks") },
            )
        }
        Section("privacy", "Privacy and security", openId, { openId = it }) {
            val pin by container.settings.pin.collectAsStateWithLifecycle()
            val autoLock by container.settings.autoLockSeconds.collectAsStateWithLifecycle()
            val recents by container.settings.recentsMode.collectAsStateWithLifecycle()
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent), // lets the glass glow show through
                modifier = Modifier.clickable { if (pin == null) pinDialog = PinDialog.Set else pinDialog = PinDialog.CheckThenMenu }.testTag("app-lock"),
                leadingContent = { Icon(Icons.Outlined.Lock, null) }, headlineContent = { Text("App lock") },
                supportingContent = { Text(if (pin == null) "Off. Set a PIN to lock the app" else "On: ${pin!!.length}-digit PIN. Tap to change or remove it") },
            )
            val biometric by container.settings.biometricUnlock.collectAsStateWithLifecycle()
            if (pin != null && BiometricGate.available(context)) ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { container.settings.setBiometricUnlock(!biometric) }.testTag("biometric-unlock"),
                leadingContent = { Icon(Icons.Outlined.Fingerprint, null) }, headlineContent = { Text("Unlock with fingerprint or face") },
                supportingContent = { Text("The PIN always works as a fallback") },
                trailingContent = { Switch(biometric, { container.settings.setBiometricUnlock(it) }) },
            )
            if (pin != null) ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { showAutoLock = true }.testTag("auto-lock"),
                leadingContent = { Icon(Icons.Outlined.Timer, null) }, headlineContent = { Text("Lock after") },
                supportingContent = { Text(autoLockLabel(autoLock)) },
            )
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { showRecents = true }.testTag("recents-mode"),
                leadingContent = { Icon(Icons.Outlined.VisibilityOff, null) }, headlineContent = { Text("In the recent apps list") },
                supportingContent = { Text("${recents.label}. Screenshots inside the app always work") },
            )
        }
        Section("data", "Data and sync", openId, { openId = it }) {
            val sheetUrl by container.settings.spreadsheetUrl.collectAsStateWithLifecycle()
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent), // lets the glass glow show through
                modifier = Modifier.clickable(onClick = onSync).testTag("open-sync"),
                leadingContent = { Icon(Icons.Outlined.CloudSync, null) }, headlineContent = { Text("Google Sheets") },
                supportingContent = { Text(if (sheetUrl == null) "Not connected" else "Connected") },
            )
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent), // lets the glass glow show through
                modifier = Modifier.clickable { picker.launch(arrayOf("*/*")) }.testTag("import"),
                leadingContent = { Icon(Icons.Outlined.FileUpload, null) }, headlineContent = { Text("Import .mmbackup") },
                supportingContent = { Text("Adds rows the phone does not have; nothing is overwritten or deleted") },
            )
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent), // lets the glass glow show through
                modifier = Modifier.clickable { backupPicker.launch("moneytracker-${java.time.LocalDate.now()}.db") }.testTag("backup"),
                leadingContent = { Icon(Icons.Outlined.Save, null) }, headlineContent = { Text("Back up to a file") },
                supportingContent = { Text("A copy of all data on the phone; keep it somewhere safe. It is not encrypted") },
            )
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent), // lets the glass glow show through
                modifier = Modifier.clickable { restorePicker.launch(arrayOf("*/*")) }.testTag("restore"),
                leadingContent = { Icon(Icons.Outlined.Restore, null) }, headlineContent = { Text("Restore from a backup file") },
                supportingContent = { Text("Replaces everything on the phone with the file's data") },
            )
        }
        Section("about", "About", openId, { openId = it }) {
            val availableUpdate by container.settings.availableUpdate.collectAsStateWithLifecycle()
            val newer = availableUpdate?.takeIf { tag ->
                val latest = parseVersion(tag)
                val mine = parseVersion(container.installedVersion)
                latest != null && mine != null && isNewer(latest, mine)
            }
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent), // lets the glass glow show through
                modifier = Modifier.clickable(onClick = onUpdates).testTag("open-updates"),
                leadingContent = { Icon(Icons.Outlined.SystemUpdate, null) }, headlineContent = { Text("App updates") },
                supportingContent = { Text(if (newer != null) "Version ${newer.removePrefix("v")} is available" else "Version ${container.installedVersion}") },
            )
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(onClick = onFeedback).testTag("open-feedback"),
                leadingContent = { Icon(Icons.Outlined.BugReport, null) }, headlineContent = { Text("Report a bug or suggest a feature") },
                supportingContent = { Text("Opens a GitHub issue") },
            )
        }
    }
    when (pinDialog) {
        PinDialog.Set -> PinSetupDialog({ pinDialog = null }) { container.lock.setPin(it); pinDialog = null }
        PinDialog.CheckThenMenu -> PinCheckDialog(container.lock, container.settings.pin.value?.length ?: 4, { pinDialog = null }) { pinDialog = PinDialog.Menu }
        PinDialog.Menu -> AlertDialog(
            onDismissRequest = { pinDialog = null }, title = { Text("App lock") },
            confirmButton = { TextButton({ pinDialog = PinDialog.Set }, modifier = Modifier.testTag("change-pin")) { Text("Change PIN") } },
            dismissButton = { TextButton({ container.lock.removePin(); pinDialog = null }, modifier = Modifier.testTag("remove-pin")) { Text("Remove PIN") } },
        )
        null -> Unit
    }
    if (showAutoLock) ChoiceDialog("Lock after", AUTO_LOCK_CHOICES.map { it to autoLockLabel(it) }, container.settings.autoLockSeconds.value, { container.settings.setAutoLockSeconds(it) }) { showAutoLock = false }
    if (showRecents) ChoiceDialog("In the recent apps list", RecentsMode.entries.map { it to it.label }, container.settings.recentsMode.value, { container.settings.setRecentsMode(it) }) { showRecents = false }
    if (showIconStyle) IconStyleDialog(container.settings, glass) { showIconStyle = false }
    if (showWidgetStyle) WidgetStyleDialog(container.settings, onChosen = {
        scope.launch {
            dev.yernur.moneytracker.widget.updateAllWidgets(context.applicationContext)
            dev.yernur.moneytracker.widget.updateWidgetPreviews(context.applicationContext)
        }
    }) { showWidgetStyle = false }
    if (showHistoryPeriod) HistoryPeriodDialog(container.settings) { showHistoryPeriod = false }
    if (showWalletConsent) WalletConsentDialog(container.settings, context) { showWalletConsent = false }
    if (showWalletAccount) {
        val accounts by remember { container.db.accountDao().observeLive() }.collectAsStateWithLifecycle(emptyList())
        ChoiceDialog("Default account for payments", accounts.map { it.uid to it.title }, container.settings.walletAccountUid.value.orEmpty(), { container.settings.setWalletAccount(it) }) { showWalletAccount = false }
    }
    if (showReminder) ReminderDialog(container.settings, context.applicationContext) { showReminder = false }
    if (showWarnings) WarningSettingsDialog(container.settings) { showWarnings = false }
    staged?.let { (file, info) ->
        AlertDialog(
            onDismissRequest = { file.delete(); staged = null }, containerColor = glassContainer(AlertDialogDefaults.containerColor),
            title = { Text("Restore this backup?") },
            text = {
                GlassWindowBlur()
                Text("${info.transactions} transactions and ${info.accounts} accounts. Everything now on the phone is replaced; the app restarts. The current data is kept once as a .bak file next to the database.")
            },
            confirmButton = {
                TextButton({
                    staged = null
                    scope.launch {
                        withContext(Dispatchers.IO) { backups?.restore(file) }
                        restartApp(context)
                    }
                }, modifier = Modifier.testTag("confirm-restore")) { Text("Restore") }
            },
            dismissButton = { TextButton({ file.delete(); staged = null }) { Text("Cancel") } },
        )
    }
    message?.let {
        AlertDialog(
            onDismissRequest = { message = null }, containerColor = glassContainer(AlertDialogDefaults.containerColor),
            confirmButton = { TextButton({ message = null }) { Text("OK") } }, text = { GlassWindowBlur(); Text(it) },
        )
    }
}

/** The database was swapped under the running app: start fresh in a new process. */
private fun restartApp(context: android.content.Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)!!.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(launch)
    android.os.Process.killProcess(android.os.Process.myPid())
}

private enum class PinDialog { Set, CheckThenMenu, Menu }

private val AUTO_LOCK_CHOICES = listOf(0, 30, 60, 300)

private fun autoLockLabel(seconds: Int) = when (seconds) {
    0 -> "Immediately"
    30 -> "After 30 seconds away"
    60 -> "After 1 minute away"
    else -> "After ${seconds / 60} minutes away"
}

@Composable
private fun <T> ChoiceDialog(title: String, choices: List<Pair<T, String>>, current: T, onPick: (T) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        containerColor = glassContainer(AlertDialogDefaults.containerColor), onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            GlassWindowBlur()
            Column {
                choices.forEach { (value, label) ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(value); onDismiss() }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(current == value, { onPick(value); onDismiss() })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Close") } },
    )
}

/** A settings group: a tappable header that opens its rows below it; [openId] is the one open group (null = all closed). */
@Composable
private fun Section(id: String, title: String, openId: String?, onOpen: (String?) -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val open = openId == id
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "section-chevron")
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(if (open) null else id) }.testTag("section-$id").padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Icon(Icons.Outlined.ExpandMore, if (open) "Collapse" else "Expand", Modifier.rotate(turn))
    }
    AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) { Column(content = content) }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

/** The consent step: what is read, what is not, then Android's own notification-access screen (only the owner can grant that). */
@Composable
private fun WalletConsentDialog(settings: AppSettings, context: android.content.Context, onDismiss: () -> Unit) {
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    AlertDialog(
        containerColor = glassContainer(AlertDialogDefaults.containerColor),
        onDismissRequest = onDismiss,
        title = { Text("Read Google Wallet payments?") },
        text = {
            GlassWindowBlur()
            Text(
                "Money Tracker will read notifications from Google Wallet only, to offer each card payment as an expense " +
                    "(you choose the category). Notifications from every other app are ignored without being read. " +
                    "Nothing is sent over the network, and you can turn this off here at any time.\n\n" +
                    "Next, Android asks you to allow notification access for Money Tracker.",
            )
        },
        confirmButton = {
            TextButton({
                settings.setWalletCapture(true)
                if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS) // the payment prompts are notifications
                }
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                onDismiss()
            }, Modifier.testTag("wallet-allow")) { Text("Allow") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderDialog(settings: AppSettings, context: android.content.Context, onDismiss: () -> Unit) {
    val enabled by settings.reminderEnabled.collectAsStateWithLifecycle()
    val minute by settings.reminderMinute.collectAsStateWithLifecycle()
    var on by remember { mutableStateOf(enabled) }
    val time = rememberTimePickerState(minute / 60, minute % 60, is24Hour = true)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    AlertDialog(
        containerColor = glassContainer(AlertDialogDefaults.containerColor),
        onDismissRequest = onDismiss,
        title = { Text("Daily reminder") },
        text = {
            GlassWindowBlur()
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Remind me to add expenses", Modifier.weight(1f))
                    Switch(on, { on = it }, Modifier.testTag("reminder-switch"))
                }
                if (on) TimeInput(time)
            }
        },
        confirmButton = {
            TextButton({
                settings.setReminder(on, time.hour * 60 + time.minute)
                ReminderWorker.schedule(context, settings, replace = true)
                if (on && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS) // asked once at first launch; ask again now that it matters
                }
                onDismiss()
            }, Modifier.testTag("reminder-save")) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
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

/** The looks to pick from (the glass pair while liquid glass is on, the everyday pair otherwise), each previewed with real icons. */
@Composable
private fun IconStyleDialog(settings: AppSettings, glass: Boolean, onDismiss: () -> Unit) {
    val current by (if (glass) settings.glassIconStyle else settings.iconStyle).collectAsStateWithLifecycle()
    val choices = if (glass) listOf(IconStyle.Crystal, IconStyle.Frost) else IconStyle.entries.filter { !it.forGlass }
    val samples = listOf("cafe" to 8, "transport" to 4, "home" to 6, "products" to 5, "heart" to 1)
    fun choose(style: IconStyle) = if (glass) settings.setGlassIconStyle(style) else settings.setIconStyle(style)
    val backdrop = Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer))
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = glassContainer(AlertDialogDefaults.containerColor),
        title = { Text("Icon style") },
        text = {
            GlassWindowBlur()
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                choices.forEach { style ->
                    Column(Modifier.fillMaxWidth().clickable { choose(style) }.testTag("icon-style-${style.name}")) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(current == style, { choose(style) })
                            Text(style.label)
                        }
                        CompositionLocalProvider(LocalIconStyle provides style) {
                            // Glass needs something colorful behind it to look like glass
                            Row(
                                Modifier.padding(start = 12.dp).then(if (glass) Modifier.clip(RoundedCornerShape(16.dp)).background(backdrop).padding(8.dp) else Modifier),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
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

/** The three widget looks as radio rows; picking one redraws the placed widgets at once. */
@Composable
private fun WidgetStyleDialog(settings: AppSettings, onChosen: () -> Unit, onDismiss: () -> Unit) {
    val current by settings.widgetStyle.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = glassContainer(AlertDialogDefaults.containerColor),
        title = { Text("Widget style") },
        text = {
            GlassWindowBlur()
            Column {
                WidgetStyle.entries.forEach { style ->
                    Row(
                        Modifier.fillMaxWidth().clickable { settings.setWidgetStyle(style); onChosen() }.testTag("widget-style-${style.name}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(current == style, { settings.setWidgetStyle(style); onChosen() })
                        Column { Text(style.label); Text(style.description, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Close") } },
    )
}
