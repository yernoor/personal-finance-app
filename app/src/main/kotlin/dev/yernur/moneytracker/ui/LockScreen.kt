package dev.yernur.moneytracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.yernur.moneytracker.data.security.AppLock
import dev.yernur.moneytracker.data.security.Pin
import dev.yernur.moneytracker.data.security.UnlockResult

/** Dots for what was typed so far, then a 3x4 keypad. [dots] is how many dots to draw (the PIN length, or the maximum while choosing one). */
@Composable
fun PinPad(value: String, dots: Int, onChange: (String) -> Unit, message: String? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.height(20.dp)) {
            repeat(dots) { i ->
                Box(
                    Modifier.size(14.dp).clip(CircleShape)
                        .background(if (i < value.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
        Text(message.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.height(24.dp))
        listOf("123", "456", "789", " 0<").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { key ->
                    Box(Modifier.size(68.dp), contentAlignment = Alignment.Center) {
                        when (key) {
                            ' ' -> Unit
                            '<' -> IconButton({ onChange(value.dropLast(1)) }, modifier = Modifier.testTag("pin-delete")) { Icon(Icons.AutoMirrored.Outlined.Backspace, "Delete") }
                            else -> Box(
                                Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { if (value.length < dots) onChange(value + key) }.testTag("pin-$key"),
                                contentAlignment = Alignment.Center,
                            ) { Text(key.toString(), style = MaterialTheme.typography.headlineSmall) }
                        }
                    }
                }
            }
        }
    }
}

private fun waitText(seconds: Long) = if (seconds >= 60) "Too many tries. Wait ${(seconds + 59) / 60} min." else "Too many tries. Wait $seconds s."

/** Covers the whole app until the right PIN is typed; checks by itself once as many digits as the stored PIN has are in. */
@Composable
fun LockScreen(lock: AppLock, length: Int, biometric: Boolean = false) {
    var typed by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(typed) {
        if (typed.length != length) return@LaunchedEffect
        when (val result = lock.attempt(typed)) {
            UnlockResult.Ok -> Unit
            UnlockResult.Wrong -> message = "Wrong PIN"
            is UnlockResult.Wait -> message = waitText(result.seconds)
        }
        typed = ""
    }
    val activity = LocalContext.current as? FragmentActivity
    val canUseBiometric = biometric && activity != null && BiometricGate.available(activity)
    fun askBiometric() { if (activity != null) BiometricGate.prompt(activity) { lock.unlockWithBiometric() } }
    // Asks as soon as the lock screen shows; "Use PIN" in the prompt (or cancelling) leaves the keypad.
    LaunchedEffect(canUseBiometric) { if (canUseBiometric) askBiometric() }
    // Its own window: dialogs and sheets left open when the app was left are windows above the app's, and would show over a lock drawn inside it.
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize().testTag("lock-screen")) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("Enter your PIN", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(24.dp))
                PinPad(typed, length, { typed = it; message = null }, message)
                if (canUseBiometric) {
                    Spacer(Modifier.height(12.dp))
                    TextButton({ askBiometric() }, modifier = Modifier.testTag("use-biometric")) { Text("Use fingerprint or face") }
                }
            }
        }
    }
}

/** Choose a PIN (4-6 digits, typed twice). Calls [onSet] with the confirmed PIN. */
@Composable
fun PinSetupDialog(onDismiss: () -> Unit, onSet: (String) -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var typed by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (first == null) "Choose a PIN" else "Type it again") },
        text = { PinPad(typed, Pin.MAX_LENGTH, { typed = it; message = null }, message) },
        confirmButton = {
            TextButton({
                val firstPin = first
                when {
                    firstPin == null -> { first = typed; typed = "" }
                    firstPin == typed -> onSet(typed)
                    else -> { first = null; typed = ""; message = "The two PINs differ. Start again." }
                }
            }, enabled = typed.length >= Pin.MIN_LENGTH, modifier = Modifier.testTag("pin-confirm")) { Text(if (first == null) "Next" else "Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

/** Asks for the current PIN before a change; calls [onOk] when it is right. */
@Composable
fun PinCheckDialog(lock: AppLock, length: Int, onDismiss: () -> Unit, onOk: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(typed) {
        if (typed.length != length) return@LaunchedEffect
        when (val result = lock.attempt(typed)) {
            UnlockResult.Ok -> onOk()
            UnlockResult.Wrong -> message = "Wrong PIN"
            is UnlockResult.Wait -> message = waitText(result.seconds)
        }
        typed = ""
    }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Enter your current PIN") },
        text = { PinPad(typed, length, { typed = it; message = null }, message) },
        confirmButton = {}, dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
