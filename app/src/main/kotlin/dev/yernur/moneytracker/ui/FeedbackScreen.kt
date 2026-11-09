package dev.yernur.moneytracker.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.yernur.moneytracker.LocalAppContainer
import dev.yernur.moneytracker.data.feedback.FeedbackKind
import dev.yernur.moneytracker.data.feedback.crashTitle
import dev.yernur.moneytracker.data.feedback.deviceInfo
import dev.yernur.moneytracker.data.feedback.issueUrl
import dev.yernur.moneytracker.data.update.UpdateChecker

/** A bug report or an idea, handed to GitHub as a pre-filled issue: the app sends nothing itself and holds no token. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(reportLastCrash: Boolean = false, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val crash = remember { if (reportLastCrash) container.crashes?.pending() else null }
    var kind by remember { mutableStateOf(if (crash != null) FeedbackKind.Crash else FeedbackKind.Bug) }
    var title by remember { mutableStateOf(crash?.let(::crashTitle) ?: "") }
    var details by remember { mutableStateOf(crash?.let { "What I was doing when it closed:\n\n\n```\n$it\n```" } ?: "") }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Report a bug or suggest a feature") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } })
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (if (crash != null) listOf(FeedbackKind.Crash) else listOf(FeedbackKind.Bug, FeedbackKind.Idea)).forEach {
                    FilterChip(kind == it, { kind = it }, { Text(it.title) }, modifier = Modifier.testTag("kind-${it.name}"))
                }
            }
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth().testTag("feedback-title"), label = { Text("Short summary") }, singleLine = true)
            OutlinedTextField(
                details, { details = it }, Modifier.fillMaxWidth().testTag("feedback-details"), minLines = 5,
                label = { Text(if (kind == FeedbackKind.Bug) "What happened, and what did you expect?" else "What would you like the app to do?") },
            )
            if (crash != null) Text("The technical details are filled in below: the error type and where it happened, no messages or ledger data. Read them, and add what you were doing.", style = MaterialTheme.typography.bodySmall)
            Text(
                "This opens a GitHub issue on a public page, with the app and Android version added. Anyone can read it, so leave out amounts, account names and other personal details. You need a GitHub account to submit it.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("feedback-note"),
            )
            Button(
                {
                    val info = deviceInfo(container.installedVersion, Build.VERSION.RELEASE, Build.VERSION.SDK_INT, Build.MODEL)
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(issueUrl(UpdateChecker.REPO, kind, title.trim(), details.trim(), info))))
                    if (crash != null) container.crashes?.clear()
                },
                Modifier.fillMaxWidth().testTag("send-feedback"), enabled = title.isNotBlank(),
            ) { Text("Open on GitHub") }
        }
    }
}

/** On the first screen after a crash: offers the report once; "Not now" throws the saved trace away. */
@Composable
fun CrashPrompt(onReport: () -> Unit) {
    val crashes = LocalAppContainer.current.crashes ?: return
    var show by remember { mutableStateOf(crashes.pending() != null) }
    if (!show) return
    AlertDialog(
        onDismissRequest = { }, title = { Text("The app closed unexpectedly") },
        text = { Text("Send a report so it can be fixed? You see everything before it goes anywhere.") },
        confirmButton = { TextButton({ show = false; onReport() }, Modifier.testTag("crash-report")) { Text("Review report") } },
        dismissButton = { TextButton({ crashes.clear(); show = false }, Modifier.testTag("crash-dismiss")) { Text("Not now") } },
        modifier = Modifier.testTag("crash-prompt"),
    )
}
