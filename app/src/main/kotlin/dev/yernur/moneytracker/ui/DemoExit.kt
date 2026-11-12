package dev.yernur.moneytracker.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.yernur.moneytracker.LocalAppContainer

/** A tap on the top bar's Demo label: asks "Return to your data?", then the PIN when app lock is set, then restarts on the real data. */
@Composable
fun DemoExitPrompt(onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    var askPin by remember { mutableStateOf(false) }
    val pin = container.settings.pin.value
    if (askPin && pin != null) {
        PinCheckDialog(container.lock, pin.length, onDismiss) { container.switchDemoMode(false) }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = glassContainer(AlertDialogDefaults.containerColor),
        title = { Text("Return to your data?") },
        text = {
            GlassWindowBlur()
            Text("You are looking at made-up demo data. The app restarts on your own data" + if (pin != null) ", after your PIN." else ".")
        },
        confirmButton = {
            TextButton({ if (pin != null) askPin = true else container.switchDemoMode(false) }, modifier = Modifier.testTag("confirm-demo-exit")) { Text("Return") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Stay in the demo") } },
    )
}
