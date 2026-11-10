package dev.yernur.moneytracker.ui

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dev.yernur.moneytracker.data.Oklch
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/** Experimental liquid glass look; provided by the app root from the setting, read by dialogs and sheets. */
val LocalGlass = staticCompositionLocalOf { false }

/** Room the floating glass bar takes at the bottom, so scrolling content can pass under it and still be reachable. */
val LocalChromeInset = staticCompositionLocalOf { 0.dp }

val GlassBarInset = 96.dp

/**
 * A glass surface: blurs and refracts what [backdrop] holds behind it, under a translucent tint.
 * The [shape] must have rounded corners (the lens effect needs them); [refraction] cannot exceed the corner radius.
 */
@Composable
fun Modifier.glass(
    backdrop: Backdrop,
    shape: Shape,
    refraction: Dp = 16.dp,
    amount: Dp = 32.dp,
    tint: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        vibrancy()
        blur(6.dp.toPx())
        lens(refraction.toPx(), amount.toPx())
    },
    onDrawSurface = { drawRect(tint) },
)

/**
 * The light behind the app in glass mode, drawn on screen and recorded into the backdrop that the glass surfaces bend.
 * With a colored accent: four soft orbs of neighbouring hues (an iridescent wash). With a grey accent (Nothing OS's monochrome theme,
 * where orbs would just be grey smudges): only a restrained sheen from above, so the surface still reads as lit glass.
 */
class Glow private constructor(private val base: Color, private val orbs: List<Orb>, private val sheen: Float) {
    /** [x], [y] and [radius] are fractions of the width (radius) and of the width and height (position). */
    class Orb(val x: Float, val y: Float, val radius: Float, val color: Color)

    fun draw(scope: DrawScope, width: Float, height: Float) = with(scope) {
        val area = Size(width, height)
        drawRect(base, size = area)
        orbs.forEach { drawRect(Brush.radialGradient(listOf(it.color, Color.Transparent), Offset(it.x * width, it.y * height), it.radius * width), size = area) }
        if (sheen > 0f) drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = sheen), Color.Transparent), 0f, height * 0.55f), size = area)
    }

    companion object {
        private const val COLORED = 0.04f // below this chroma the accent is a grey

        fun of(surface: Color, accent: Color, dark: Boolean): Glow {
            val argb = accent.toArgb()
            val hue = Oklch.hueOf(argb)
            if (Oklch.chromaOf(argb) < COLORED) {
                // Monochrome. Dark: lift the top of the screen a little and put one very faint wide light low on the right.
                // Light: a cool grey shade in two corners, so the white glass cards have something to stand out from.
                if (dark) return Glow(surface, listOf(Orb(0.85f, 0.95f, 0.9f, Color.White.copy(alpha = 0.05f))), sheen = 0.10f)
                val shade = Color(0xFF8F96A3)
                return Glow(surface, listOf(Orb(0.1f, 0.04f, 1.1f, shade.copy(alpha = 0.30f)), Orb(0.92f, 0.92f, 1.0f, shade.copy(alpha = 0.24f))), sheen = 0f)
            }
            fun orb(x: Float, y: Float, radius: Float, shift: Float, alpha: Float): Orb {
                val color = Color(Oklch.toArgb(if (dark) 0.42f else 0.84f, if (dark) 0.11f else 0.12f, (hue + shift + 360f) % 360f))
                return Orb(x, y, radius, color.copy(alpha = alpha))
            }
            return Glow(
                surface,
                listOf(orb(0.12f, 0.08f, 0.95f, 0f, 0.60f), orb(0.98f, 0.42f, 0.85f, 55f, 0.45f), orb(0.05f, 0.88f, 0.95f, -50f, 0.45f), orb(0.85f, 0.98f, 0.70f, 20f, 0.35f)),
                sheen = 0f,
            )
        }
    }
}

/** The glow, for the screens in the navigation host: each draws its own opaque copy of it, so a screen that is sliding away never lets the one below show through. */
val LocalGlow = staticCompositionLocalOf<Glow?> { null }

/**
 * In glass mode every Scaffold must be see-through: its default background is an opaque flat color, which would hide the glow that each
 * screen draws for itself (and leave a seam under the status bar). The nested theme keeps the press feedback, which M3 would reset to a ripple.
 */
@Composable
fun GlassScaffoldTheme(glass: Boolean, content: @Composable () -> Unit) {
    if (!glass) return content()
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(background = Color.Transparent), typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        CompositionLocalProvider(LocalIndication provides PressScaleIndication, content = content)
    }
}

/** Container color for dialogs and sheets: translucent with glass on, the usual one otherwise. */
@Composable
fun glassContainer(default: Color): Color = if (LocalGlass.current) MaterialTheme.colorScheme.surface.copy(alpha = 0.72f) else default

/**
 * Call inside a dialog or sheet: with glass on, the system blurs everything behind its window (frosted look).
 * Needs Android 12+ and cross-window blur enabled on the device; otherwise the translucent tint is all you get.
 */
@Composable
fun GlassWindowBlur(radius: Dp = 24.dp) {
    if (!LocalGlass.current) return
    val view = LocalView.current
    SideEffect {
        val root = view.rootView
        val params = root.layoutParams as? WindowManager.LayoutParams ?: return@SideEffect
        val windowManager = view.context.getSystemService(WindowManager::class.java)
        if (!windowManager.isCrossWindowBlurEnabled) return@SideEffect
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        params.blurBehindRadius = (radius.value * view.resources.displayMetrics.density).toInt()
        windowManager.updateViewLayout(root, params)
    }
}
