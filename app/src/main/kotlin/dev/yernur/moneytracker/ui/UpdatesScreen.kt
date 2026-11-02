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
import dev.yernur.moneytracker.data.update.InstallStatus
import dev.yernur.moneytracker.data.update.Release
import dev.yernur.moneytracker.data.update.UpdateChecker
import dev.yernur.moneytracker.data.update.UpdateException
import dev.yernur.moneytracker.data.update.UpdateInstaller
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
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
    private val settings: AppSettings,
) : ViewModel() {
    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

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
                }
                .onFailure { state = UpdateState.Failed(describe(it)) }
        }
    }

    // shortcut: the download lives in this screen's scope, so leaving the screen cancels it; move it to a service if 49 MB on a slow link becomes a problem
    fun install(release: Release) {
        val installer = installer ?: return
        val apk = release.apk ?: run { state = UpdateState.Failed("This release has no APK attached."); return }
        state = UpdateState.Downloading(0f)
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val file = installer.download(apk) { state = UpdateState.Downloading(it) }
                    state = UpdateState.Installing
                    installer.install(file)
                }
            }.onFailure { state = UpdateState.Failed(describe(it)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(onBack: () -> Unit) {
    val container = dev.yernur.moneytracker.LocalAppContainer.current
    val vm = appViewModel { UpdatesViewModel(it.updateChecker, it.updateInstaller, it.settings) }
    val context = LocalContext.current
    val state = vm.state
    var needsPermission by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (vm.state == UpdateState.Idle) vm.check() } // opening the screen is asking

    Scaffold(topBar = {
        TopAppBar(title = { Text("App updates") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } })
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
                    if (needsPermission) Text("Allow this app to install updates in the settings that just opened, then come back and tap Download and install.")
                }
                is UpdateState.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Downloading the update")
                    LinearProgressIndicator({ state.progress }, Modifier.fillMaxWidth().testTag("download-progress"))
                }
                UpdateState.Installing -> Text("Installing. Android may ask you to confirm; the app restarts when it is done.", modifier = Modifier.testTag("installing"))
                is UpdateState.Failed -> Text(state.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("update-error"))
            }
            val busy = state is UpdateState.Checking || state is UpdateState.Downloading || state == UpdateState.Installing
            OutlinedButton(vm::check, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("check-updates")) { Text("Check for updates") }
        }
    }
}
