package dev.yernur.moneytracker.ui

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

/** How much wider the chosen tab is than the others (it shows its label; the others only their icon). */
private const val CHOSEN_WEIGHT = 2.3f
private val barSpring = spring<Float>(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)

/**
 * The bottom bar as a floating pill: the chosen tab opens up to icon and label while the others shrink to their icon, and one highlight
 * slides between them. Works for 2 to 5 tabs.
 */
@Composable
fun FloatingTabBar(tabs: List<NavTab>, selected: NavTab, onSelect: (NavTab) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(32.dp)
    val chosen = tabs.indexOf(selected).coerceAtLeast(0)
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 4.dp)) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(64.dp).shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
                .clip(shape).background(scheme.surface).border(1.dp, scheme.outlineVariant, shape).padding(8.dp),
        ) {
            val weights = tabs.indices.map { if (it == chosen) CHOSEN_WEIGHT else 1f }
            val total = weights.sum()
            val highlightX by animateDpAsState(maxWidth * weights.take(chosen).sum() / total, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow), label = "highlight-x")
            val highlightW by animateDpAsState(maxWidth * weights[chosen] / total, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow), label = "highlight-w")
            Box(Modifier.offset(x = highlightX).width(highlightW).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(scheme.secondaryContainer))
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

/** The round accent add button that floats over the right end of the bar. */
@Composable
fun AddButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier.size(60.dp).shadow(10.dp, CircleShape, ambientColor = scheme.primary.copy(alpha = 0.4f), spotColor = scheme.primary.copy(alpha = 0.4f))
            .clip(CircleShape).background(scheme.primary).clickable(role = Role.Button, onClick = onClick).testTag("add"),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.Add, "Add", Modifier.size(26.dp), tint = scheme.onPrimary) }
}
