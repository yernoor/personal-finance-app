package dev.yernur.moneytracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

val CardShape = RoundedCornerShape(22.dp)

/**
 * The app's card: a plain surface with a hairline border and generous corners, instead of the grey slab. A card that is given its own
 * [colors] (a warning, a result, a verdict) keeps them and has no border.
 */
@Composable
fun AppCard(modifier: Modifier = Modifier, shape: Shape = CardShape, colors: CardColors? = null, content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    if (colors != null) return Card(modifier, shape, colors, content = content)
    Card(
        modifier, shape,
        CardDefaults.cardColors(containerColor = if (isDarkSurface()) scheme.surfaceContainerLow else scheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.6f)),
        content = content,
    )
}
