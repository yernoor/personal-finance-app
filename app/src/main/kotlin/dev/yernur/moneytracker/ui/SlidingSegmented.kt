package dev.yernur.moneytracker.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * A segmented selector whose thumb slides (with a small overshoot) to the chosen segment instead of the choice jumping.
 * [tags] are the test tags of the segments, in order.
 */
@Composable
fun SlidingSegmented(labels: List<String>, selected: Int, tags: List<String>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(18.dp)
    BoxWithConstraints(modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(22.dp)).background(ink.copy(alpha = 0.07f)).padding(4.dp)) {
        val segment = maxWidth / labels.size
        val thumbX by animateDpAsState(segment * selected.coerceIn(0, labels.lastIndex), spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "thumb")
        Box(Modifier.offset(x = thumbX).width(segment).fillMaxHeight().shadow(1.dp, shape).background(MaterialTheme.colorScheme.surface, shape))
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { index, label ->
                val on = index == selected
                val color by animateColorAsState(if (on) ink else MaterialTheme.colorScheme.onSurfaceVariant, label = "segment-text")
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(shape).selectable(on, role = Role.Tab, onClick = { onSelect(index) }).testTag(tags[index]),
                    contentAlignment = Alignment.Center,
                ) { Text(label, maxLines = 1, style = MaterialTheme.typography.labelLarge, color = color) }
            }
        }
    }
}
