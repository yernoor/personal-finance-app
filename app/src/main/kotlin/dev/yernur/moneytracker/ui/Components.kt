package dev.yernur.moneytracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathParser
import dev.yernur.moneytracker.data.AccentPalette
import dev.yernur.moneytracker.data.IconStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Slider
import dev.yernur.moneytracker.data.Oklch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.yernur.moneytracker.AppContainer
import dev.yernur.moneytracker.LocalAppContainer

@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = LocalAppContainer.current
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

/** The look icons have right now: the owner's choice, or Glass whenever liquid glass is on. Provided by the app root. */
val LocalIconStyle = staticCompositionLocalOf { IconStyle.Solid }

/** Hue of the accent, in OKLCH degrees: the category colors are spread around it. Provided by the app root. */
val LocalAccentHue = staticCompositionLocalOf { 265f }

@Composable
fun isDarkSurface() = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/** The strong color of a stored color key for the current accent (chart segments, dots, swatches). */
@Composable
fun themedColor(colorKey: Int?): Color {
    val hue = LocalAccentHue.current
    val dark = isDarkSurface()
    return remember(colorKey, hue, dark) { AccentPalette.pick(colorKey, hue).let { Color(AccentPalette.main(it.slot, it.baseHue, dark)) } }
}

@Composable
fun TextInputDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        containerColor = glassContainer(AlertDialogDefaults.containerColor),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { GlassWindowBlur();  OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.testTag("dialog-input")) },
        confirmButton = { TextButton({ onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text("OK") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        containerColor = glassContainer(AlertDialogDefaults.containerColor),
        onDismissRequest = onDismiss,
        text = { GlassWindowBlur();  Text(text) },
        confirmButton = { TextButton(onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun cacheDir() = LocalContext.current.cacheDir

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(selected: Int?, onPick: (Int) -> Unit) {
    var customOpen by remember { mutableStateOf(false) }
    val rainbow = Brush.sweepGradient(listOf(Color(0xFFE53935), Color(0xFFFDD835), Color(0xFF43A047), Color(0xFF00ACC1), Color(0xFF1E88E5), Color(0xFFD81B60), Color(0xFFE53935)))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CategoryPalette.forEach { argb ->
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(themedColor(argb))
                    .then(if (argb == selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .clickable { onPick(argb) },
            )
        }
        // Any other color, typed as hex: shown as its own swatch while it is the selected one
        val custom = AccentPalette.isCustom(selected)
        Box(
            Modifier.size(36.dp).clip(CircleShape).then(if (custom) Modifier.background(themedColor(selected)) else Modifier.background(rainbow))
                .then(if (custom) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                .clickable { customOpen = true }.testTag("color-custom"),
            contentAlignment = Alignment.Center,
        ) { Text("#", color = Color.White, style = MaterialTheme.typography.labelLarge) }
    }
    if (customOpen) CustomColorDialog(selected, onPick) { customOpen = false }
}

private fun parseHex(text: String): Int? =
    text.trim().removePrefix("#").takeIf { it.length == 6 }?.toIntOrNull(16)?.let { 0xFF000000.toInt() or it }

/** An exact color: a hex field and a hue slider that edit the same value. The icon styles then draw with that hue. */
@Composable
private fun CustomColorDialog(selected: Int?, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("%06X".format((if (AccentPalette.isCustom(selected)) selected!! else 0xFF3E7BFA.toInt()) and 0xFFFFFF)) }
    val parsed = parseHex(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = glassContainer(AlertDialogDefaults.containerColor),
        title = { Text("Custom color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(parsed?.let { Color(it) } ?: Color.Transparent).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                    OutlinedTextField(
                        text, { text = it.trim().removePrefix("#").take(6).uppercase() }, label = { Text("Hex") }, prefix = { Text("#") },
                        singleLine = true, isError = parsed == null, modifier = Modifier.testTag("custom-hex"),
                    )
                }
                Text("Hue", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = parsed?.let { Oklch.hueOf(it) } ?: 0f, onValueChange = { text = "%06X".format(Oklch.toArgb(0.65f, 0.15f, it) and 0xFFFFFF) },
                    valueRange = 0f..360f, modifier = Modifier.testTag("custom-hue"),
                )
            }
        },
        confirmButton = { TextButton({ parsed?.let { onPick(it); onDismiss() } }, enabled = parsed != null, modifier = Modifier.testTag("custom-use")) { Text("Use") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

/** An icon in a picker: the selected one is ringed, the others are dimmed. */
@Composable
fun PickableIcon(name: String, colorKey: Int?, selected: Boolean) {
    IconBadge(
        name, colorKey, size = 44.dp,
        modifier = if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape).padding(2.dp) else Modifier.alpha(0.6f),
    )
}
