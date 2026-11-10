package dev.yernur.moneytracker.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.update.DownloadStatus
import dev.yernur.moneytracker.data.update.InstallStatus
import dev.yernur.moneytracker.data.update.Release
import dev.yernur.moneytracker.data.update.UpdateChecker
import dev.yernur.moneytracker.data.update.UpdateDownloads
import dev.yernur.moneytracker.data.update.UpdateException
import dev.yernur.moneytracker.data.update.UpdateInstaller
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data class Downloaded(val release: Release, val file: java.io.File) : UpdateState
    data object Installing : UpdateState
    data class Failed(val message: String) : UpdateState
}

private fun describe(e: Throwable) = when (e) {
    is UpdateException -> e.message.orEmpty()
    is IOException -> "No internet connection."
    else -> e.message ?: "Something went wrong."
}

class UpdatesViewModel(
    private val checker: UpdateChecker?,
    private val installer: UpdateInstaller?,
    private val downloads: UpdateDownloads?,
    private val settings: AppSettings,
    /** Runs on the IO thread right before the installer takes over: the pre-update database copy. A failure must not block the update. */
    private val beforeInstall: () -> Unit = {},
) : ViewModel() {
    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    private var watching: Job? = null

    init {
        // The installer answers in a broadcast, possibly after this screen was left: show a parked failure, once.
        viewModelScope.launch {
            InstallStatus.failure.filterNotNull().collect {
                state = UpdateState.Failed(it)
                InstallStatus.failure.value = null
            }
        }
    }

    fun check() {
        val checker = checker ?: return
        state = UpdateState.Checking
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { checker.check() } }
                .onSuccess {
                    settings.setAvailableUpdate(it?.tag)
                    state = if (it == null) UpdateState.UpToDate else UpdateState.Available(it)
                    if (it != null) watch(it) // a download started earlier (screen left, phone locked) is picked up again
                }
                .onFailure { state = UpdateState.Failed(describe(it)) }
        }
    }

    /** The download runs in WorkManager, not in this screen: here it is only started and watched. */
    fun install(release: Release) {
        val apk = release.apk ?: run { state = UpdateState.Failed("This release has no APK attached."); return }
        val downloads = downloads ?: return
        state = UpdateState.Downloading(0f)
        downloads.start(apk)
        watch(release)
    }

    /** A finished download is already on disk: hand it over. */
    fun installDownloaded(file: java.io.File) {
        val installer = installer ?: return
        state = UpdateState.Installing
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { runCatching(beforeInstall); installer.install(file) } }.onFailure { state = UpdateState.Failed(describe(it)) }
        }
    }

    private fun watch(release: Release) {
        val apk = release.apk ?: return
        val downloads = downloads ?: return
        watching?.cancel()
        watching = viewModelScope.launch {
            downloads.status(apk).collect { status ->
                val started = state is UpdateState.Downloading // a download begun on this screen goes straight to the installer
                when (status) {
                    DownloadStatus.Idle -> Unit
                    is DownloadStatus.Running -> state = UpdateState.Downloading(status.progress)
                    is DownloadStatus.Done -> if (started) installDownloaded(status.file) else if (state is UpdateState.Available) state = UpdateState.Downloaded(release, status.file)
                    is DownloadStatus.Failed -> if (started) state = UpdateState.Failed(status.message)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(onBack: () -> Unit) {
    val container = dev.yernur.moneytracker.LocalAppContainer.current
    val vm = appViewModel { UpdatesViewModel(it.updateChecker, it.updateInstaller, it.updateDownloads, it.settings) { it.backups?.writeAutoBackup() } }
    val context = LocalContext.current
    val state = vm.state
    var needsPermission by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (vm.state == UpdateState.Idle) vm.check() } // opening the screen is asking

    Scaffold(topBar = {
        AppTopBar(title = { Text("App updates") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } })
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Installed version ${container.installedVersion}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("installed-version"))
            when (state) {
                UpdateState.Idle -> Unit
                UpdateState.Checking -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator()
                    Text("Asking GitHub for the latest release")
                }
                UpdateState.UpToDate -> Text("You have the latest version.", modifier = Modifier.testTag("up-to-date"))
                is UpdateState.Available -> Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("update-available")) {
                    Text("Version ${state.release.tag.removePrefix("v")} is available", style = MaterialTheme.typography.titleMedium)
                    Button(
                        {
                            // Android only lets an app install packages after the owner allows it for this app in Settings ("Install unknown apps")
                            if (context.packageManager.canRequestPackageInstalls()) vm.install(state.release)
                            else {
                                needsPermission = true
                                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                            }
                        },
                        Modifier.fillMaxWidth().testTag("install-update"),
                    ) { Text("Download and install") }
                    OutlinedButton(
                        { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.release.pageUrl))) },
                        Modifier.fillMaxWidth().testTag("open-release-page"),
                    ) { Text("Release page") }
                    ReleaseNotesCard(state.release)
                    if (needsPermission) Text("Allow this app to install updates in the settings that just opened, then come back and tap Download and install.")
                }
                is UpdateState.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Downloading the update. You can leave this screen or lock the phone; it keeps going.")
                    LinearProgressIndicator({ state.progress }, Modifier.fillMaxWidth().testTag("download-progress"))
                }
                is UpdateState.Downloaded -> Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("update-downloaded")) {
                    Text("Version ${state.release.tag.removePrefix("v")} is downloaded", style = MaterialTheme.typography.titleMedium)
                    Button({ vm.installDownloaded(state.file) }, Modifier.fillMaxWidth().testTag("install-downloaded")) { Text("Install") }
                    ReleaseNotesCard(state.release)
                }
                UpdateState.Installing -> Text("Installing. Android may ask you to confirm; the app restarts when it is done.", modifier = Modifier.testTag("installing"))
                is UpdateState.Failed -> Text(state.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("update-error"))
            }
            val busy = state is UpdateState.Checking || state is UpdateState.Downloading || state == UpdateState.Installing
            OutlinedButton(vm::check, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("check-updates")) { Text("Check for updates") }
        }
    }
}

@Composable
private fun ReleaseNotesCard(release: Release) {
    if (release.notes.isEmpty()) return
    Text("What's new", style = MaterialTheme.typography.titleSmall)
    ReleaseNotes(release.notes, Modifier.testTag("release-notes"))
}
