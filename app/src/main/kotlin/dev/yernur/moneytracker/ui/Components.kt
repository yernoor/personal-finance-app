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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
val LocalIconStyle = staticCompositionLocalOf { IconStyle.Vivid }

/** Hue of the accent, in OKLCH degrees: the category colors are spread around it. Provided by the app root. */
val LocalAccentHue = staticCompositionLocalOf { 265f }

@Composable
private fun isDarkSurface() = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/** The strong color of a stored color key for the current accent (chart segments, dots, swatches). */
@Composable
fun themedColor(colorKey: Int?): Color {
    val hue = LocalAccentHue.current
    val dark = isDarkSurface()
    return remember(colorKey, hue, dark) { Color(AccentPalette.main(AccentPalette.slotOf(colorKey), hue, dark)) }
}

private val parsedPaths = HashMap<String, Path>()
private fun pathOf(data: String): Path = parsedPaths.getOrPut(data) { PathParser().parsePathString(data).toPath() }

private const val GLYPH_STROKE = 1.9f // in 24-unit glyph space

/**
 * A category or account icon in the current [LocalIconStyle]. [colorKey] is the color stored with the item (one of the
 * backup's 12); the actual color comes from the accent (see AccentPalette).
 */
@Composable
fun IconBadge(iconName: String?, colorKey: Int?, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val style = LocalIconStyle.current
    val hue = LocalAccentHue.current
    val dark = isDarkSurface()
    val slot = AccentPalette.slotOf(colorKey)
    val glyph = AppIcons.glyph(iconName)
    val neutral = MaterialTheme.colorScheme.surfaceContainerHighest
    val outline = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        val scale = side * 0.56f / 24f
        val inset = (side - 24f * scale) / 2f
        val stroke = Stroke(width = GLYPH_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun glyphIn(strokeColor: Color, fill: Color? = null) = withTransform({
            translate(inset, inset)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            fill?.let { drawPath(pathOf(glyph.body), it) }
            drawPath(pathOf(glyph.body), strokeColor, style = stroke)
            if (glyph.detail.isNotEmpty()) drawPath(pathOf(glyph.detail), strokeColor, style = stroke)
        }
        val tile = CornerRadius(side * 0.3f)
        when (style) {
            IconStyle.Vivid -> {
                val disc = AccentPalette.main(slot, hue, dark)
                drawCircle(Color(disc))
                glyphIn(Color(AccentPalette.glyphOn(disc)))
            }
            IconStyle.Tonal -> {
                drawRoundRect(Color(AccentPalette.tonalBackground(slot, hue, dark)), cornerRadius = tile)
                glyphIn(Color(AccentPalette.tonalGlyph(slot, hue, dark)))
            }
            IconStyle.Duotone -> {
                drawRoundRect(neutral, cornerRadius = tile)
                drawRoundRect(outline, cornerRadius = tile, style = Stroke(1.dp.toPx()))
                glyphIn(Color(AccentPalette.duotoneStroke(slot, hue, dark)), fill = Color(AccentPalette.duotoneFill(slot, hue, dark)))
            }
            IconStyle.Glass -> {
                val disc = AccentPalette.main(slot, hue, dark)
                val tint = Color(disc)
                drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.92f), tint.copy(alpha = 0.58f)), Offset(side * 0.36f, side * 0.28f), side * 0.85f))
                // Light catching the top edge, and a bright rim, are what make it read as glass
                drawOval(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), 0f, side * 0.42f),
                    topLeft = Offset(side * 0.18f, side * 0.06f), size = Size(side * 0.64f, side * 0.38f),
                )
                drawCircle(Color.White.copy(alpha = 0.8f), radius = side / 2 - 0.75.dp.toPx(), style = Stroke(1.2.dp.toPx()))
                glyphIn(Color(AccentPalette.glyphOn(disc)))
            }
        }
    }
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
    }
}

/** An icon in a picker: the selected one is ringed, the others are dimmed. */
@Composable
fun PickableIcon(name: String, colorKey: Int?, selected: Boolean) {
    IconBadge(
        name, colorKey, size = 44.dp,
        modifier = if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape).padding(2.dp) else Modifier.alpha(0.6f),
    )
}
