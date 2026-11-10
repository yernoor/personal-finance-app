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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import dev.yernur.moneytracker.data.Oklch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

/** The "Color" heading and picker. Mono draws every icon in the accent, so a color would change nothing there and the section is left out. */
@Composable
fun ColorSection(selected: Int?, onPick: (Int) -> Unit) {
    if (LocalIconStyle.current == IconStyle.Mono) return
    Text("Color", style = MaterialTheme.typography.labelLarge)
    ColorPicker(selected, onPick)
}

private const val WHEEL_LIGHTNESS = 0.70f
private const val WHEEL_CHROMA = 0.15f
private const val WHEEL_START_HUE = 250f

/**
 * A color wheel: drag around the ring to pick a hue, the circle in the middle shows the result. Only the hue is chosen, because the
 * icon styles set lightness and strength themselves; a gray is one of the 12 stored colors.
 */
@Composable
private fun CustomColorDialog(selected: Int?, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    var hue by remember { mutableFloatStateOf(if (AccentPalette.isCustom(selected)) Oklch.hueOf(selected!!) else WHEEL_START_HUE) }
    val chosen = Color(Oklch.toArgb(WHEEL_LIGHTNESS, WHEEL_CHROMA, hue))
    val ring = remember { Brush.sweepGradient((0..24).map { Color(Oklch.toArgb(WHEEL_LIGHTNESS, WHEEL_CHROMA, it * 15f)) }) }
    val outline = MaterialTheme.colorScheme.onSurface
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = glassContainer(AlertDialogDefaults.containerColor),
        title = { Text("Custom color") },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Canvas(
                    Modifier.size(240.dp).testTag("color-wheel").pointerInput(Unit) {
                        awaitEachGesture {
                            fun point(p: Offset) { hue = ((Math.toDegrees(atan2(p.y - size.height / 2.0, p.x - size.width / 2.0)) + 360) % 360).toFloat() }
                            val down = awaitFirstDown()
                            point(down.position)
                            do {
                                val event = awaitPointerEvent()
                                event.changes.firstOrNull()?.let { point(it.position); it.consume() }
                            } while (event.changes.any { it.pressed })
                        }
                    },
                ) {
                    val band = 40.dp.toPx()
                    val radius = (size.minDimension - band) / 2
                    drawCircle(ring, radius, style = Stroke(band))
                    val angle = Math.toRadians(hue.toDouble())
                    val thumb = center + Offset((radius * cos(angle)).toFloat(), (radius * sin(angle)).toFloat())
                    drawCircle(Color.White, band / 2 + 2.dp.toPx(), thumb)
                    drawCircle(outline.copy(alpha = 0.35f), band / 2 + 2.dp.toPx(), thumb, style = Stroke(1.dp.toPx()))
                    drawCircle(chosen, band / 2 - 3.dp.toPx(), thumb)
                    drawCircle(chosen, radius - band / 2 - 14.dp.toPx(), center) // the result, large, in the middle
                }
            }
        },
        confirmButton = { TextButton({ onPick(Oklch.toArgb(WHEEL_LIGHTNESS, WHEEL_CHROMA, hue)); onDismiss() }, modifier = Modifier.testTag("custom-use")) { Text("Use") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

/** An icon in a picker: the selected one is ringed, the others are dimmed. */
@Composable
fun PickableIcon(name: String, colorKey: Int?, selected: Boolean, onClick: () -> Unit) {
    // Not an IconButton: that clips its content to a circle, which cuts the corners off the tile (and off an Outline tile's ring)
    Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        IconBadge(
            name, colorKey, size = 44.dp,
            modifier = if (selected) Modifier else Modifier.alpha(0.6f),
            ringColor = if (selected) MaterialTheme.colorScheme.primary else null,
        )
    }
}
