package dev.yernur.moneytracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import android.view.animation.OvershootInterpolator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop

/** How much wider the chosen tab is than the others (it shows its label; the others only their icon). */
private const val CHOSEN_WEIGHT = 2.3f
private val barSpring = spring<Float>(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)

/**
 * The bottom bar as a floating pill: the chosen tab opens up to icon and label while the others shrink to their icon, and one highlight
 * slides between them. Works for 2 to 5 tabs.
 */
@Composable
fun FloatingTabBar(tabs: List<NavTab>, selected: NavTab, onSelect: (NavTab) -> Unit, modifier: Modifier = Modifier, backdrop: Backdrop? = null) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(32.dp)
    val chosen = tabs.indexOf(selected).coerceAtLeast(0)
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 4.dp)) {
        // With glass on, the bar is a lens over what scrolls under it; otherwise a plain surface
        val surface = if (backdrop != null) Modifier.clip(shape).glass(backdrop, shape).border(1.dp, Color.White.copy(alpha = if (isDarkSurface()) 0.18f else 0.7f), shape)
        else Modifier.shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
            .clip(shape).background(scheme.surface).border(1.dp, scheme.outlineVariant, shape)
        BoxWithConstraints(Modifier.fillMaxWidth().height(64.dp).then(surface).padding(8.dp)) {
            val weights = tabs.indices.map { if (it == chosen) CHOSEN_WEIGHT else 1f }
            val total = weights.sum()
            val highlightX by animateDpAsState(maxWidth * weights.take(chosen).sum() / total, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow), label = "highlight-x")
            val highlightW by animateDpAsState(maxWidth * weights[chosen] / total, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow), label = "highlight-w")
            Box(Modifier.offset(x = highlightX).width(highlightW).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(scheme.secondaryContainer.copy(alpha = if (backdrop != null) 0.7f else 1f)))
            Row(Modifier.fillMaxWidth().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEachIndexed { index, tab ->
                    val on = index == chosen
                    val weight by animateFloatAsState(weights[index], barSpring, label = "tab-weight")
                    val tint by animateColorAsState(if (on) scheme.onSecondaryContainer else scheme.onSurfaceVariant, label = "tab-tint")
                    Row(
                        Modifier.weight(weight).fillMaxHeight().clip(RoundedCornerShape(24.dp))
                            .selectable(on, role = Role.Tab, onClick = { onSelect(tab) }).testTag("tab-${tab.id}"),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(tab.icon, if (on) null else tab.label, Modifier.size(22.dp), tint = tint)
                        AnimatedVisibility(on, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                            Row {
                                Spacer(Modifier.width(8.dp))
                                Text(tab.label, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge, color = tint)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The round accent add button that floats over the right end of the bar. A tap adds an expense ([onClick]); holding it ticks and rolls three
 * pills up above it (Expense, Income, Transfer, nearest first), each opening the add screen of that kind through [onPickKind].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AddButton(onClick: () -> Unit, modifier: Modifier = Modifier, backdrop: Backdrop? = null, onPickKind: (EntryKind) -> Unit = {}) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    val turn by animateFloatAsState(if (menu) 45f else 0f, tween(260, easing = FastOutSlowInEasing), label = "add-turn")
    val look = if (backdrop != null) Modifier.clip(CircleShape).glass(backdrop, CircleShape, refraction = 14.dp, amount = 22.dp, tint = scheme.primary.copy(alpha = 0.55f))
        .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape)
    else Modifier.shadow(10.dp, CircleShape, ambientColor = scheme.primary.copy(alpha = 0.4f), spotColor = scheme.primary.copy(alpha = 0.4f)).clip(CircleShape).background(scheme.primary)
    Box(
        modifier.size(60.dp).then(look)
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true })
            .testTag("add"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Add, "Add", Modifier.size(26.dp).rotate(turn), tint = if (backdrop != null) Color.White else scheme.onPrimary)
        if (menu) AddMenu(onDismiss = { menu = false }) { menu = false; onPickKind(it) }
    }
}

private val ADD_MENU = listOf(
    Triple(EntryKind.Expense, Icons.Outlined.ArrowDownward, Semantic.expense),
    Triple(EntryKind.Income, Icons.Outlined.ArrowUpward, Semantic.income),
    Triple(EntryKind.Transfer, Icons.Outlined.SwapHoriz, null),
)

/** The three pills, popped above the button; each rolls up from it with a small overshoot, 40 ms after the one below. */
@Composable
private fun AddMenu(onDismiss: () -> Unit, onPick: (EntryKind) -> Unit) {
    val density = LocalDensity.current
    val position = remember(density) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
                IntOffset(anchorBounds.right - popupContentSize.width, anchorBounds.top - popupContentSize.height - with(density) { 12.dp.roundToPx() })
        }
    }
    Popup(position, onDismissRequest = onDismiss) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ADD_MENU.reversed().forEach { (kind, icon, color) ->
                val delay = ADD_MENU.indexOfFirst { it.first == kind } * 40
                val progress = remember { Animatable(0f) }
                LaunchedEffect(Unit) { progress.animateTo(1f, tween(280, delay, easing = { OvershootInterpolator(1.6f).getInterpolation(it) })) }
                val tint = color ?: MaterialTheme.colorScheme.primary
                Row(
                    Modifier.graphicsLayer {
                        alpha = progress.value.coerceIn(0f, 1f); scaleX = 0.6f + 0.4f * progress.value; scaleY = scaleX
                        translationY = (1f - progress.value) * 90.dp.toPx(); transformOrigin = TransformOrigin(1f, 1f)
                    },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        kind.name, Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable { onPick(kind) }
                            .padding(horizontal = 12.dp, vertical = 6.dp).testTag("add-${kind.name}"),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Box(Modifier.size(44.dp).clip(CircleShape).background(tint).clickable { onPick(kind) }, contentAlignment = Alignment.Center) {
                        Icon(icon, kind.name, tint = Color.White)
                    }
                }
            }
        }
    }
}
