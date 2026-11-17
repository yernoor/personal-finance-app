package dev.yernur.moneytracker.ui

import androidx.compose.runtime.DisposableEffect

import androidx.compose.ui.semantics.onLongClick

import androidx.compose.ui.semantics.onClick

import androidx.compose.ui.semantics.role

import androidx.compose.ui.semantics.semantics

import androidx.compose.ui.unit.toSize

import androidx.compose.ui.geometry.Rect

import androidx.compose.ui.geometry.Offset

import androidx.compose.ui.layout.positionOnScreen

import androidx.compose.ui.layout.onGloballyPositioned

import androidx.compose.ui.input.pointer.PointerInputChange

import androidx.compose.foundation.gestures.waitForUpOrCancellation

import androidx.compose.foundation.gestures.awaitFirstDown

import androidx.compose.foundation.gestures.awaitEachGesture

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.MutableState
import androidx.compose.ui.unit.Dp
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
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberUpdatedState
import kotlin.math.roundToInt

/** How much wider the chosen tab is than the others (it shows its label; the others only their icon). */
private const val CHOSEN_WEIGHT = 2.3f
private val barSpring = spring<Float>(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)

/**
 * The bottom bar as a floating pill: the chosen tab opens up to icon and label while the others shrink to their icon, and one highlight
 * slides between them. Works for 2 to 5 tabs.
 * Sliding a finger along the bar scrubs: the tabs flatten to equal slots (so they stay put under the finger), the highlight lifts and follows,
 * and each slot crossed ticks and opens its tab. Letting go drops the highlight into place. Design: the "Navbar scrub" prototype artifact.
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
        val haptic = LocalHapticFeedback.current
        val current by rememberUpdatedState(selected) // the gesture outlives recompositions: read the latest
        val select by rememberUpdatedState(onSelect)
        var scrubX by remember { mutableStateOf<Float?>(null) } // finger x while scrubbing, null otherwise
        val scrubbing = scrubX != null
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(64.dp).then(surface).padding(8.dp).testTag("tab-bar").pointerInput(tabs) {
                val slot = size.width.toFloat() / tabs.size
                var last = -1
                fun at(x: Float) = (x / slot).toInt().coerceIn(0, tabs.lastIndex)
                detectHorizontalDragGestures(
                    onDragStart = { scrubX = it.x; last = -1 },
                    onDragEnd = { scrubX = null }, onDragCancel = { scrubX = null },
                ) { change, delta ->
                    change.consume() // the screen's own swipe must not also fire
                    val x = (scrubX ?: change.position.x) + delta
                    scrubX = x
                    val i = at(x)
                    if (i != last) {
                        if (last != -1) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        last = i
                        if (tabs[i] != current) select(tabs[i])
                    }
                }
            },
        ) {
            val weights = tabs.indices.map { if (it == chosen && !scrubbing) CHOSEN_WEIGHT else 1f }
            val total = weights.sum()
            val widthPx = constraints.maxWidth.toFloat()
            val restX = widthPx * weights.take(chosen).sum() / total
            val restW = widthPx * weights[chosen] / total
            val highlightX = remember { Animatable(restX) }
            val highlightW = remember { Animatable(restW) }
            val slotW = widthPx / tabs.size
            val x = scrubX
            if (x != null) LaunchedEffect(x) { // follows the finger at once; only the width eases to one slot
                highlightX.snapTo((x - slotW / 2).coerceIn(0f, widthPx - slotW))
                highlightW.animateTo(slotW, tween(160))
            } else LaunchedEffect(restX, restW) {
                launch { highlightX.animateTo(restX, barSpring) }
                highlightW.animateTo(restW, barSpring)
            }
            val lift by animateFloatAsState(if (scrubbing) 1.08f else 1f, barSpring, label = "highlight-lift")
            val density = LocalDensity.current
            Box(
                Modifier.offset { IntOffset(highlightX.value.roundToInt(), 0) }.width(with(density) { highlightW.value.toDp() }).fillMaxHeight()
                    .graphicsLayer { scaleX = lift; scaleY = lift }
                    .shadow(if (scrubbing) 6.dp else 0.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp)).background(scheme.secondaryContainer.copy(alpha = if (backdrop != null) 0.7f else 1f)),
            )
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
                        val pop by animateFloatAsState(if (on && scrubbing) 1.18f else 1f, barSpring, label = "tab-pop")
                        Icon(tab.icon, if (on) null else tab.label, Modifier.size(22.dp).graphicsLayer { scaleX = pop; scaleY = pop }, tint = tint)
                        AnimatedVisibility(on && !scrubbing, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
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
 * The add menu: whether it is open, the pill a finger still held from the long press is over, and where each pill is on the screen
 * (screen coordinates, since without glass the pills live in a popup window of their own).
 */
class AddMenuState {
    var open by mutableStateOf(false)
    var hovered by mutableStateOf<EntryKind?>(null)
    internal val bounds = mutableMapOf<EntryKind, Rect>()

    internal fun pillAt(screen: Offset): EntryKind? = bounds.entries.firstOrNull { it.value.contains(screen) }?.key
}

/**
 * The round accent add button that floats over the right end of the bar. A tap adds an expense ([onClick]); holding it ticks and rolls three
 * pills up above it (Expense, Income, Transfer, nearest first), each opening the add screen of that kind through [onPickKind]. Without lifting
 * the finger, slide onto a pill (it pops, with a tick) and let go to open it; let go anywhere else and the menu stays open for a tap.
 * Without glass the pills pop up in a [Popup]. With glass the caller draws [GlassAddMenu] from [menu] in the app's own window instead: a popup is a
 * separate window, and the glass lens can only bend what is in the window it draws in.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AddButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, backdrop: Backdrop? = null, onPickKind: (EntryKind) -> Unit = {},
    menu: AddMenuState = remember { AddMenuState() },
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val turn by animateFloatAsState(if (menu.open) 45f else 0f, tween(260, easing = FastOutSlowInEasing), label = "add-turn")
    val tap by rememberUpdatedState { if (menu.open) menu.open = false else onClick() } // with the menu open, the button (now an x) closes it
    val pick by rememberUpdatedState(onPickKind)
    var origin by remember { mutableStateOf(Offset.Zero) } // the button's top left on the screen
    val look = if (backdrop != null) Modifier.clip(CircleShape).glass(backdrop, CircleShape, refraction = 14.dp, amount = 22.dp, tint = scheme.primary.copy(alpha = 0.55f))
        .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape)
    else Modifier.shadow(10.dp, CircleShape, ambientColor = scheme.primary.copy(alpha = 0.4f), spotColor = scheme.primary.copy(alpha = 0.4f)).clip(CircleShape).background(scheme.primary)
    Box(
        modifier.size(60.dp).then(look)
            .onGloballyPositioned { origin = it.positionOnScreen() }
            .semantics {
                role = Role.Button
                onClick { tap(); true }
                onLongClick { menu.open = true; true }
            }
            .pointerInput(menu) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var up: PointerInputChange? = null
                    val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { up = waitForUpOrCancellation(); false } == null
                    if (!held) { up?.let { it.consume(); tap() }; return@awaitEachGesture }
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menu.open = true
                    // Still holding: follow the finger over the pills.
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break
                        val over = menu.pillAt(origin + change.position)
                        if (over != menu.hovered) {
                            if (over != null) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            menu.hovered = over
                        }
                    }
                    val chosen = menu.hovered
                    menu.hovered = null
                    if (chosen != null) { menu.open = false; pick(chosen) }
                }
            }
            .testTag("add"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Add, "Add", Modifier.size(26.dp).rotate(turn), tint = if (backdrop != null) Color.White else scheme.onPrimary)
        if (menu.open && backdrop == null) AddMenu(menu, onDismiss = { menu.open = false }) { menu.open = false; onPickKind(it) }
    }
}

private val ADD_MENU = listOf(
    Triple(EntryKind.Expense, Icons.Outlined.ArrowDownward, Semantic.expense),
    Triple(EntryKind.Income, Icons.Outlined.ArrowUpward, Semantic.income),
    Triple(EntryKind.Transfer, Icons.Outlined.SwapHoriz, null),
)

/** The pills, popped above the button (no glass). */
@Composable
private fun AddMenu(menu: AddMenuState, onDismiss: () -> Unit, onPick: (EntryKind) -> Unit) {
    val density = LocalDensity.current
    val position = remember(density) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
                IntOffset(anchorBounds.right - popupContentSize.width, anchorBounds.top - popupContentSize.height - with(density) { 12.dp.roundToPx() })
        }
    }
    Popup(position, onDismissRequest = onDismiss) { AddMenuPills(null, menu, onPick) }
}

/**
 * Glass mode's menu, for a full-screen spot in the app's window above the content: the screen behind blurs (a tap on it closes the menu, as does back)
 * and the glass pills, their bottom [bottom] above the screen's bottom edge, lined up with the button's right edge [end] from the screen's.
 */
@Composable
fun GlassAddMenu(backdrop: Backdrop, menu: AddMenuState, bottom: Dp, end: Dp, onDismiss: () -> Unit, onPick: (EntryKind) -> Unit) {
    BackHandler(onBack = onDismiss)
    // The screen behind blurs in (like the frosted window behind the Wallet and calculator sheets): a full-screen glass with blur and no lens.
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, tween(220, easing = FastOutSlowInEasing)) }
    val dim = Color.Black.copy(alpha = if (isDarkSurface()) 0.25f else 0.10f)
    Box(
        Modifier.fillMaxSize()
            .drawBackdrop(backdrop, shape = { RectangleShape }, effects = { blur((18.dp.toPx() * shown.value).coerceAtLeast(0.5f)) }, onDrawSurface = { drawRect(dim.copy(alpha = dim.alpha * shown.value)) })
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss).testTag("add-menu-scrim"),
    ) {
        AddMenuPills(backdrop, menu, onPick, Modifier.align(Alignment.BottomEnd).padding(end = end - 8.dp, bottom = bottom - 8.dp))
    }
}

private val PILL_SHAPE = RoundedCornerShape(16.dp)

/**
 * The three pills; each rolls up from the button with a small overshoot, 40 ms after the one below. With [backdrop] they are glass.
 * The one a held finger is over ([AddMenuState.hovered]) pops a little larger, the same spring as the navbar's scrub.
 */
@Composable
private fun AddMenuPills(backdrop: Backdrop?, menu: AddMenuState, onPick: (EntryKind) -> Unit, modifier: Modifier = Modifier) {
    DisposableEffect(menu) { onDispose { menu.bounds.clear() } }
    val scheme = MaterialTheme.colorScheme
    val rim = Color.White.copy(alpha = if (isDarkSurface()) 0.22f else 0.7f)
    Column(modifier.padding(8.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ADD_MENU.reversed().forEach { (kind, icon, color) ->
            val delay = ADD_MENU.indexOfFirst { it.first == kind } * 40
            val progress = remember { Animatable(0f) }
            LaunchedEffect(Unit) { progress.animateTo(1f, tween(280, delay, easing = { OvershootInterpolator(1.6f).getInterpolation(it) })) }
            val tint = color ?: scheme.primary
            val pop by animateFloatAsState(if (menu.hovered == kind) 1.12f else 1f, barSpring, label = "pill-pop")
            val label = if (backdrop != null) Modifier.clip(PILL_SHAPE).glass(backdrop, PILL_SHAPE, refraction = 10.dp, amount = 18.dp).border(1.dp, rim, PILL_SHAPE)
            else Modifier.clip(RoundedCornerShape(10.dp)).background(scheme.surfaceContainerHigh)
            val bubble = if (backdrop != null) Modifier.clip(CircleShape).glass(backdrop, CircleShape, refraction = 12.dp, amount = 20.dp, tint = tint.copy(alpha = 0.6f))
                .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape)
            else Modifier.clip(CircleShape).background(tint)
            Row(
                Modifier.onGloballyPositioned { menu.bounds[kind] = Rect(it.positionOnScreen(), it.size.toSize()) }.graphicsLayer {
                    alpha = progress.value.coerceIn(0f, 1f); scaleX = (0.6f + 0.4f * progress.value) * pop; scaleY = scaleX
                    translationY = (1f - progress.value) * 90.dp.toPx(); transformOrigin = TransformOrigin(1f, 1f)
                },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    kind.name, label.clickable { onPick(kind) }
                        .padding(horizontal = 14.dp, vertical = if (backdrop != null) 8.dp else 6.dp).testTag("add-${kind.name}"),
                    style = MaterialTheme.typography.labelLarge,
                )
                Box(Modifier.size(44.dp).then(bubble).clickable { onPick(kind) }, contentAlignment = Alignment.Center) {
                    Icon(icon, kind.name, tint = Color.White)
                }
            }
        }
    }
}
