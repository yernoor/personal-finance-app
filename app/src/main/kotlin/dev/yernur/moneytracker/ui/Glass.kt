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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

/** Soft accent glow behind the app so glass has color to bend; drawn on screen and recorded into the backdrop. */
fun glowBackground(surface: Color, primary: Color, tertiary: Color): DrawScope.() -> Unit = {
    drawRect(surface)
    drawRect(Brush.radialGradient(listOf(primary.copy(alpha = 0.42f), Color.Transparent), Offset(size.width * 0.1f, size.height * 0.12f), size.width * 0.9f))
    drawRect(Brush.radialGradient(listOf(tertiary.copy(alpha = 0.34f), Color.Transparent), Offset(size.width * 0.95f, size.height * 0.62f), size.width * 0.85f))
}

@Composable
fun GlassBottomBar(backdrop: Backdrop, tabs: List<NavTab>, selected: NavTab, onSelect: (NavTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp).height(64.dp)
            .glass(backdrop, RoundedCornerShape(32.dp)).padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val isSelected = tab == selected
            Column(
                Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(26.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f) else Color.Transparent)
                    .clickable { onSelect(tab) }.testTag("tab-${tab.id}"),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Icon(tab.icon, null, Modifier.size(22.dp), tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(tab.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun GlassAddButton(backdrop: Backdrop, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(56.dp).glass(backdrop, RoundedCornerShape(28.dp), refraction = 12.dp, amount = 24.dp)
            .clip(RoundedCornerShape(28.dp)).clickable(onClick = onClick).testTag("add"),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.Add, "Add", tint = MaterialTheme.colorScheme.onSurface) }
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
