package dev.yernur.moneytracker.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/** The steps of the Home "Get set up" card, in the guide's order. */
enum class SetupStep(val label: String) {
    Accounts("Add your accounts"),
    Categories("Set up categories"),
    FirstOperation("Log your first expense"),
    Capture("Turn on payment capture"),
    Analysis("Look at Analysis and Recurring"),
}

/** Which steps are done, read from the data and settings (nothing to tick by hand). */
fun doneSetupSteps(accounts: Int, categories: Int, hasOperation: Boolean, capture: Boolean, analysisSeen: Boolean): Set<SetupStep> = buildSet {
    if (accounts > 0) add(SetupStep.Accounts)
    if (categories > 0) add(SetupStep.Categories)
    if (hasOperation) add(SetupStep.FirstOperation)
    if (capture) add(SetupStep.Capture)
    if (analysisSeen) add(SetupStep.Analysis)
}

/** The "Get set up" card: progress, the five steps (done ones struck through, the rest open their screen), and a way to hide it. */
@Composable
fun SetupCard(done: Set<SetupStep>, onStep: (SetupStep) -> Unit, onHide: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val total = SetupStep.entries.size
    AppCard(Modifier.fillMaxWidth().testTag("home-setup"), colors = CardDefaults.cardColors(containerColor = scheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Get set up", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text("${done.size} of $total", style = MaterialTheme.typography.labelLarge, color = scheme.primary)
            }
            LinearProgressIndicator({ done.size / total.toFloat() }, Modifier.fillMaxWidth().clip(CircleShape))
            SetupStep.entries.forEach { step ->
                val isDone = step in done
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                        .clickable(enabled = !isDone, role = Role.Button) { onStep(step) }
                        .semantics { stateDescription = if (isDone) "Done" else "Not done" }
                        .padding(vertical = 10.dp).testTag("setup-${step.name}"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(24.dp).clip(CircleShape)
                            .then(if (isDone) Modifier.background(scheme.primary) else Modifier.border(2.dp, scheme.outline, CircleShape)),
                        contentAlignment = Alignment.Center,
                    ) { if (isDone) Icon(Icons.Outlined.Check, null, Modifier.size(16.dp), tint = scheme.onPrimary) }
                    Text(
                        step.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                        color = if (isDone) scheme.onSecondaryContainer.copy(alpha = 0.6f) else scheme.onSecondaryContainer,
                        textDecoration = if (isDone) TextDecoration.LineThrough else null,
                    )
                    if (!isDone) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = scheme.onSecondaryContainer)
                }
            }
            TextButton(onHide, Modifier.testTag("setup-hide")) { Text("Hide this card") }
        }
    }
}
