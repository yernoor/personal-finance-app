package dev.yernur.moneytracker.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.android.gms.common.api.ApiException
import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.google.Authorizer
import dev.yernur.moneytracker.data.google.GoogleAuthorizer
import dev.yernur.moneytracker.data.sheets.HttpTransport
import dev.yernur.moneytracker.data.sheets.PullResult
import dev.yernur.moneytracker.data.sheets.SheetsApi
import dev.yernur.moneytracker.data.sheets.SheetsException
import dev.yernur.moneytracker.data.sheets.SheetsSync
import dev.yernur.moneytracker.data.sheets.SyncRunner
import dev.yernur.moneytracker.data.sheets.UrlConnectionTransport
import java.io.IOException
import kotlinx.coroutines.launch

sealed interface SyncState {
    data object Idle : SyncState
    data class Working(val step: String) : SyncState
    /** Google wants the owner to approve access first; the screen launches [request]. */
    data class NeedsConsent(val request: IntentSenderRequest) : SyncState
    data class Failed(val message: String) : SyncState
    /** An import from the sheet finished; [summary] says what changed. */
    data class Imported(val summary: String) : SyncState
}

/** A plain-language reason for a failure, with what to do about it. */
fun describeError(e: Throwable): String = when (e) {
    is ApiException -> when (e.statusCode) {
        10 -> "Google sign-in is not set up for this build yet (no OAuth client for this package and signing key). Follow docs/GOOGLE_SETUP.md."
        12501, 16 -> "Sign-in was cancelled."
        7 -> "No connection to Google. Check the internet and try again."
        else -> "Google sign-in failed (code ${e.statusCode})."
    }
    is SheetsException -> when (e.code) {
        401 -> "Google rejected the sign-in. Try connecting again."
        403 -> "Google refused access: ${e.message}. Check that the Google Sheets API is enabled for the project (docs/GOOGLE_SETUP.md)."
        else -> "Google Sheets said: ${e.message}"
    }
    is IOException -> "No internet connection."
    else -> e.message ?: "Something went wrong."
}

class SyncViewModel(
    private val db: AppDatabase,
    private val settings: AppSettings,
    private val transport: HttpTransport = UrlConnectionTransport(),
) : ViewModel() {
    var state by mutableStateOf<SyncState>(SyncState.Idle)
        private set

    /** Step 1: get Google's permission (shows the consent screen if needed). Creates nothing. */
    fun connect(authorizer: Authorizer) {
        viewModelScope.launch {
            state = SyncState.Working("Asking Google for access")
            runCatching {
                val result = authorizer.authorize()
                val consent = result.consent
                if (consent != null) state = SyncState.NeedsConsent(IntentSenderRequest.Builder(consent).build())
                else markConnected(result.accessToken)
            }.onFailure { state = SyncState.Failed(describeError(it)) }
        }
    }

    fun onConsentResult(authorizer: Authorizer, data: Intent?) {
        viewModelScope.launch {
            runCatching { markConnected(authorizer.resultFrom(data).accessToken) }
                .onFailure { state = SyncState.Failed(describeError(it)) }
        }
    }

    /** Step 2: create the spreadsheet and upload everything. Asks Google again without a screen when the permission still stands. */
    fun createSheet(authorizer: Authorizer) {
        viewModelScope.launch {
            state = SyncState.Working("Asking Google for access")
            runCatching {
                val result = authorizer.authorize()
                val token = result.accessToken
                if (result.consent != null || token == null) {
                    settings.setGoogleConnected(false) // revoked or expired since step 1
                    state = SyncState.Failed("Google access has to be approved again. Tap Connect Google.")
                } else push(authorizer, token)
            }.onFailure { state = SyncState.Failed(describeError(it)) }
        }
    }

    /** See [SyncRunner]. */
    fun syncNow(authorizer: Authorizer) {
        viewModelScope.launch {
            runCatching {
                val pulled = SyncRunner(db, settings, transport).run(authorizer) { state = SyncState.Working(it) }
                state = SyncState.Imported(
                    if (pulled == null) "Synced. First sync: this phone's data was sent to the sheet; edits made in the sheet are read from the next sync on."
                    else "Synced.\n" + summary(pulled),
                )
            }.onFailure { state = SyncState.Failed(describeError(it)) }
        }
    }

    private fun summary(p: PullResult): String {
        val lines = mutableListOf("Imported: ${p.added} new, ${p.updated} changed, ${p.deleted} deleted.")
        if (p.warnings.isNotEmpty()) lines += "${p.warnings.size} row(s) skipped or patched:" + p.warnings.take(5).joinToString("") { "\n- $it" }
        return lines.joinToString("\n")
    }

    private fun markConnected(token: String?) {
        requireNotNull(token) { "Google did not grant access" }
        settings.setGoogleConnected(true)
        state = SyncState.Idle
    }

    fun forget() {
        settings.forgetSpreadsheet()
        state = SyncState.Idle
    }

    private suspend fun push(authorizer: Authorizer, firstToken: String) {
        val api = SheetsApi(transport, { refresh -> if (refresh) authorizer.freshToken(firstToken) else firstToken })
        SheetsSync(db, api, settings).pushAll { state = SyncState.Working(it) }
        state = SyncState.Idle
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(onBack: () -> Unit) {
    val vm = appViewModel { SyncViewModel(it.db, it.settings) }
    val container = dev.yernur.moneytracker.LocalAppContainer.current
    val context = LocalContext.current
    val spreadsheetUrl by container.settings.spreadsheetUrl.collectAsStateWithLifecycle()
    val autoSync by container.settings.autoSync.collectAsStateWithLifecycle()
    val lastSyncAt by container.settings.lastSyncAt.collectAsStateWithLifecycle()
    val googleConnected by container.settings.googleConnected.collectAsStateWithLifecycle()
    // Null on a device without Google Play services (the Authorization API cannot be created there)
    val authorizer = remember(context) { runCatching { context.findActivity()?.let(::GoogleAuthorizer) }.getOrNull() }
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        authorizer?.let { vm.onConsentResult(it, result.data) }
    }
    val state = vm.state
    LaunchedEffect(state) { if (state is SyncState.NeedsConsent) consentLauncher.launch(state.request) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Google Sheets") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } })
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Keeps a copy of your data in a Google Sheet you can open on a computer. The app can only reach the sheet it creates itself " +
                    "(the drive.file permission), never the rest of your Drive.",
                style = MaterialTheme.typography.bodyMedium,
            )
            val idle = state !is SyncState.Working
            if (spreadsheetUrl == null) {
                if (!googleConnected) {
                    Text("Step 1 of 2", style = MaterialTheme.typography.labelLarge)
                    Button(
                        { authorizer?.let(vm::connect) }, enabled = idle && authorizer != null,
                        modifier = Modifier.fillMaxWidth().testTag("sync-connect"),
                    ) { Text("Connect Google") }
                    if (authorizer == null) Text("Google Play services are not available on this device.", color = MaterialTheme.colorScheme.error)
                } else {
                    Text("Google is connected. Step 2 of 2", style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("sync-google-connected"))
                    Button(
                        { authorizer?.let(vm::createSheet) }, enabled = idle && authorizer != null,
                        modifier = Modifier.fillMaxWidth().testTag("sync-create"),
                    ) { Text("Create or refresh the sheet") }
                }
            } else {
                Card(Modifier.fillMaxWidth().testTag("sync-connected")) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Connected", style = MaterialTheme.typography.titleMedium)
                        Text(spreadsheetUrl.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        Text("Sync now reads the sheet, then writes this phone's data to it. Rows you changed on the phone since the last sync are not overwritten.", style = MaterialTheme.typography.bodySmall)
                        Button({ authorizer?.let(vm::syncNow) }, enabled = idle && authorizer != null, modifier = Modifier.fillMaxWidth().testTag("sync-now")) { Text("Sync now") }
                        Text(
                            "Last synced: ${lastSyncAt?.let { "$it UTC" } ?: "never"}",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("sync-last"),
                        )
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Sync once a week in the background", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Switch(autoSync, container.settings::setAutoSync, Modifier.testTag("sync-auto"))
                        }
                        OutlinedButton({ context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(spreadsheetUrl))) }, Modifier.fillMaxWidth().testTag("sync-open")) { Text("Open the sheet") }
                        OutlinedButton(vm::forget, Modifier.fillMaxWidth().testTag("sync-forget")) { Text("Forget this sheet (it stays in Drive)") }
                    }
                }
            }
            when (state) {
                is SyncState.Working -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator()
                    Text(state.step, Modifier.testTag("sync-step"))
                }
                is SyncState.Imported -> Text(state.summary, modifier = Modifier.testTag("sync-imported"))
                is SyncState.Failed -> Text(state.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("sync-error"))
                else -> Unit
            }
        }
    }
}
