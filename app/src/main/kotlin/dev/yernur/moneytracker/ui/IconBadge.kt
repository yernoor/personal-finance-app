package dev.yernur.moneytracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.yernur.moneytracker.data.AccentPalette
import dev.yernur.moneytracker.data.IconStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

private val parsedPaths = HashMap<String, Path>()
private fun pathOf(data: String): Path = parsedPaths.getOrPut(data) { PathParser().parsePathString(data).toPath() }

/** The iOS app-icon outline (a superellipse, not a rounded rectangle) in a 100 x 100 space. */
private val squircle: Path = Path().apply {
    val steps = 96
    for (i in 0 until steps) {
        val t = i * 2 * PI / steps
        val c = cos(t)
        val s = sin(t)
        val x = (50 + 49.6 * sign(c) * abs(c).pow(0.4)).toFloat()
        val y = (50 + 49.6 * sign(s) * abs(s).pow(0.4)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private const val TILE = 100f // the tile is drawn in a 100 x 100 space; the glyph is drawn in its own 24 x 24 space inside it
private const val GLYPH_SCALE = 2.4f
private const val GLYPH_INSET = (TILE - 24f * GLYPH_SCALE) / 2f

/**
 * Everything that differs between the looks, so one routine draws them all (mirrors the design canvas).
 * Colors carry their own alpha. A zero alpha turns a layer off.
 */
private class Look(
    val baseTop: Color, val baseBottom: Color,
    val gloss: Float = 0f,
    val glow: Color = Color.Transparent,
    val edge: Color = Color.Transparent,
    val rim: Triple<Float, Float, Float>? = null,
    val glyphTop: Color, val glyphBottom: Color,
    val knock: Color,
    val shadow: Color = Color.Transparent,
    val edgeWidth: Float = 1f,
    val bodyFill: Boolean = true, // false: shapes are only stroked (the Outline look)
)

private fun look(style: IconStyle, slot: Int, hue: Float, dark: Boolean): Look {
    fun c(argb: Int, alpha: Float = 1f) = Color(argb).copy(alpha = alpha)
    return when (style) {
        IconStyle.Springboard -> Look(
            baseTop = c(AccentPalette.springTop(slot, hue, dark)), baseBottom = c(AccentPalette.springBottom(slot, hue, dark)),
            gloss = if (dark) 0.14f else 0.30f,
            edge = Color.Black.copy(alpha = if (dark) 0.35f else 0.10f),
            rim = Triple(if (dark) 0.32f else 0.50f, 0.04f, if (dark) 0.10f else 0.16f),
            glyphTop = Color.White, glyphBottom = Color.White,
            knock = c(AccentPalette.springKnock(slot, hue, dark)),
            shadow = c(AccentPalette.springShadow(slot, hue, dark), 0.55f),
        )
        IconStyle.SoftFill -> {
            val background = c(AccentPalette.softBackground(slot, hue, dark))
            val glyph = c(AccentPalette.softGlyph(slot, hue, dark))
            Look(
                baseTop = background, baseBottom = background,
                gloss = if (dark) 0.06f else 0.45f,
                edge = glyph.copy(alpha = if (dark) 0.18f else 0.14f),
                glyphTop = glyph, glyphBottom = glyph, knock = background,
            )
        }
        IconStyle.Outline -> {
            val glyph = c(AccentPalette.softGlyph(slot, hue, dark))
            Look(
                baseTop = Color.Transparent, baseBottom = Color.Transparent,
                edge = glyph.copy(alpha = 0.55f), edgeWidth = 3f,
                glyphTop = glyph, glyphBottom = glyph, knock = Color.Transparent, bodyFill = false,
            )
        }
        IconStyle.Mono -> {
            val neutral = AccentPalette.SLOTS - 1
            val glyph = c(AccentPalette.softGlyph(0, hue, dark)) // slot 0 is the accent itself: every icon in the one color
            val background = c(AccentPalette.softBackground(neutral, hue, dark))
            Look(baseTop = background, baseBottom = background, glyphTop = glyph, glyphBottom = glyph, knock = background)
        }
        IconStyle.Sticker -> {
            val card = if (dark) Color(0xFF2A2C33) else Color.White
            Look(
                baseTop = card, baseBottom = card,
                edge = (if (dark) Color.White else Color.Black).copy(alpha = 0.10f), edgeWidth = 1.5f,
                glyphTop = c(AccentPalette.springTop(slot, hue, dark)), glyphBottom = c(AccentPalette.springBottom(slot, hue, dark)),
                knock = card,
            )
        }
        IconStyle.LiquidTile -> Look(
            baseTop = c(AccentPalette.liquidTop(slot, hue, dark), if (dark) 0.52f else 0.66f),
            baseBottom = c(AccentPalette.liquidBottom(slot, hue, dark), if (dark) 0.78f else 0.90f),
            gloss = if (dark) 0.26f else 0.55f,
            glow = c(AccentPalette.liquidGlow(slot, hue, dark), if (dark) 0.75f else 0.85f),
            edge = Color.Black.copy(alpha = if (dark) 0.30f else 0.08f),
            rim = Triple(0.95f, 0.10f, 0.60f),
            glyphTop = Color.White.copy(alpha = 0.97f), glyphBottom = Color(if (dark) 0xFFEAEAF6 else 0xFFF4F1FF).copy(alpha = 0.97f),
            knock = c(AccentPalette.liquidKnock(slot, hue, dark), 0.78f),
            shadow = c(AccentPalette.liquidShadow(slot, hue, dark), 0.55f),
        )
        IconStyle.ClearGlass -> Look(
            baseTop = Color.White.copy(alpha = if (dark) 0.12f else 0.34f), baseBottom = Color.White.copy(alpha = if (dark) 0.03f else 0.10f),
            gloss = if (dark) 0.16f else 0.45f,
            glow = c(AccentPalette.clearGlow(slot, hue, dark), if (dark) 0.35f else 0.55f),
            edge = if (dark) Color.White.copy(alpha = 0.12f) else Color(0xFF1B1B3A).copy(alpha = 0.10f),
            rim = Triple(0.95f, 0.08f, 0.65f),
            glyphTop = c(AccentPalette.clearGlyphTop(slot, hue, dark)), glyphBottom = c(AccentPalette.clearGlyphBottom(slot, hue, dark)),
            knock = c(AccentPalette.clearKnock(slot, hue, dark), 0.55f),
            shadow = c(AccentPalette.clearShadow(slot, hue, dark), if (dark) 0.55f else 0.30f),
        )
    }
}

private fun DrawScope.drawTile(look: Look, glyph: Glyph) {
    val white = Color.White
    drawPath(squircle, Brush.verticalGradient(listOf(look.baseTop, look.baseBottom), 0f, TILE))
    if (look.gloss > 0f) drawPath(squircle, Brush.verticalGradient(listOf(white.copy(alpha = look.gloss), Color.Transparent), 0f, 58f))
    if (look.glow.alpha > 0f) {
        drawPath(squircle, Brush.radialGradient(listOf(look.glow, look.glow.copy(alpha = 0f)), Offset(50f, 104f), 62f))
    }
    drawPath(squircle, look.edge, style = Stroke(look.edgeWidth))
    look.rim?.let { (start, middle, end) ->
        withTransform({
            translate(0.8f, 0.8f)
            scale(0.984f, 0.984f, pivot = Offset.Zero)
        }) {
            val rim = Brush.linearGradient(
                0f to white.copy(alpha = start), 0.45f to white.copy(alpha = middle), 1f to white.copy(alpha = end),
                start = Offset(8f, 0f), end = Offset(92f, 100f),
            )
            drawPath(squircle, rim, style = Stroke(1.5f))
        }
    }
    withTransform({
        translate(GLYPH_INSET, GLYPH_INSET)
        scale(GLYPH_SCALE, GLYPH_SCALE, pivot = Offset.Zero)
    }) {
        val body = pathOf(glyph.body)
        val detail = if (glyph.detail.isNotEmpty()) pathOf(glyph.detail) else null
        val round = { width: Float -> Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round) }
        if (look.shadow.alpha > 0f) {
            // No cheap blur in a Canvas: a few widening, fainter passes below the glyph stand in for a soft drop shadow
            withTransform({ translate(0f, 0.9f) }) {
                for ((extra, alpha) in listOf(2.2f to 0.18f, 1.2f to 0.28f, 0.4f to 0.5f)) {
                    val shade = look.shadow.copy(alpha = look.shadow.alpha * alpha)
                    drawPath(body, shade)
                    drawPath(body, shade, style = round(1.2f + extra))
                    detail?.let { drawPath(it, shade, style = round(1.9f + extra)) }
                }
            }
        }
        val fill = Brush.verticalGradient(listOf(look.glyphTop, look.glyphBottom), 3f, 21f)
        if (look.bodyFill) drawPath(body, fill)
        drawPath(body, fill, style = round(1.2f))
        detail?.let { drawPath(it, fill, style = round(1.9f)) }
        // Lines drawn inside the filled shape are cut out of it again, so the glyph keeps its inner detail
        if (detail != null) clipPath(body) { drawPath(detail, look.knock, style = round(1.5f)) }
    }
}

/**
 * A category or account icon in the current [LocalIconStyle]: an iOS-style tile with a filled glyph. [colorKey] is the color
 * stored with the item (one of the backup's 12); the actual color comes from the accent (see AccentPalette).
 */
@Composable
fun IconBadge(iconName: String?, colorKey: Int?, size: Dp = 40.dp, modifier: Modifier = Modifier, ringColor: Color? = null) {
    val style = LocalIconStyle.current
    val hue = LocalAccentHue.current
    val dark = isDarkSurface()
    val slot = AccentPalette.slotOf(colorKey)
    val glyph = AppIcons.glyph(iconName)
    val look = look(style, slot, hue, dark)
    Canvas(modifier.size(size)) {
        withTransform({ scale(this@Canvas.size.minDimension / TILE, this@Canvas.size.minDimension / TILE, pivot = Offset.Zero) }) {
            drawTile(look, glyph)
            // Selection: an outline of the tile itself (so it follows the tile shape and look), drawn just outside it.
            if (ringColor != null) withTransform({ scale(1.1f, 1.1f, pivot = Offset(50f, 50f)) }) { drawPath(squircle, ringColor, style = Stroke(5f)) }
        }
    }
}
