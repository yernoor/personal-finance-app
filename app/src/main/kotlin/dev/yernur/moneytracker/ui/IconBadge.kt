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
import dev.yernur.moneytracker.data.Oklch
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
    val bodyStroke: Float = 1.2f, // outline width of the closed shapes; the Outline look matches it to the lines so the glyph has one weight
)

/** [hue] is what the slot's formulas get (an exact color's own hue); [accentHue] is the app's accent, which Mono always uses. */
/** [argb] moved by [dl] in OKLCH lightness, keeping its hue and chroma (so a lighter yellow is still yellow). */
private fun shift(argb: Int, dl: Float, dh: Float = 0f): Int =
    Oklch.toArgb((Oklch.lab(argb).l.toFloat() + dl).coerceIn(0f, 1f), Oklch.chromaOf(argb), (Oklch.hueOf(argb) + dh + 360f) % 360f)

/** Only the lightest hues (yellow, lime) take a dark glyph; everything else keeps a white one, which looks cleaner on glass than black on teal or orange. */
private fun lightTile(argb: Int) = Oklch.lab(argb).l > 0.76

private fun look(style: IconStyle, slot: Int, hue: Float, accentHue: Float, dark: Boolean): Look {
    fun c(argb: Int, alpha: Float = 1f) = Color(argb).copy(alpha = alpha)
    return when (style) {
        IconStyle.Solid -> Look(
            baseTop = c(AccentPalette.springTop(slot, hue, dark)), baseBottom = c(AccentPalette.springBottom(slot, hue, dark)),
            gloss = if (dark) 0.14f else 0.30f,
            edge = Color.Black.copy(alpha = if (dark) 0.35f else 0.10f),
            rim = Triple(if (dark) 0.32f else 0.50f, 0.04f, if (dark) 0.10f else 0.16f),
            glyphTop = Color.White, glyphBottom = Color.White,
            knock = c(AccentPalette.springKnock(slot, hue, dark)),
            shadow = c(AccentPalette.springShadow(slot, hue, dark), 0.55f),
        )
        IconStyle.Tonal -> {
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
                glyphTop = glyph, glyphBottom = glyph, knock = Color.Transparent, bodyFill = false, bodyStroke = 1.7f,
            )
        }
        IconStyle.Mono -> {
            val neutral = AccentPalette.SLOTS - 1
            val glyph = c(AccentPalette.softGlyph(0, accentHue, dark)) // slot 0 is the accent itself: every icon in the one color
            val background = c(AccentPalette.softBackground(neutral, accentHue, dark))
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
        // Glass looks. Both start from the slot's own vivid color (right lightness for its hue), so yellow stays yellow instead of going olive.
        IconStyle.Crystal -> {
            // A gem: a translucent tile in the vivid color, lighter at the top, with a bright rim and a highlight; the glyph is white or dark by contrast
            val main = AccentPalette.main(slot, hue, dark)
            val glyph = if (lightTile(main)) c(0xFF1B1B1F.toInt()) else Color.White
            Look(
                baseTop = c(shift(main, 0.08f), 0.92f), baseBottom = c(shift(main, -0.04f), if (dark) 0.72f else 0.82f),
                gloss = if (dark) 0.30f else 0.55f,
                glow = c(shift(main, 0.14f), 0.70f),
                edge = Color.Black.copy(alpha = if (dark) 0.30f else 0.06f),
                rim = Triple(0.95f, 0.10f, 0.60f),
                glyphTop = glyph, glyphBottom = glyph.copy(alpha = 0.96f),
                knock = c(shift(main, -0.06f), 0.85f),
                shadow = c(shift(main, -0.28f), 0.45f),
            )
        }
        IconStyle.Frost -> {
            // Clear glass: an almost colorless tile with a soft bloom of the color from below, and a vivid glyph
            val main = AccentPalette.main(slot, hue, dark)
            val warm = if (!dark && lightTile(main)) -18f else 0f // a darkened yellow turns olive; nudged toward orange it stays a clean gold
            Look(
                baseTop = Color.White.copy(alpha = if (dark) 0.16f else 0.52f), baseBottom = Color.White.copy(alpha = if (dark) 0.04f else 0.18f),
                gloss = if (dark) 0.14f else 0.40f,
                glow = c(main, if (dark) 0.50f else 0.55f),
                edge = if (dark) Color.White.copy(alpha = 0.12f) else Color(0xFF1B1B3A).copy(alpha = 0.10f),
                rim = Triple(0.95f, 0.08f, 0.65f),
                glyphTop = c(shift(main, if (dark) 0.10f else -0.10f, warm)), glyphBottom = c(shift(main, if (dark) 0f else -0.20f, warm)),
                knock = Color.White.copy(alpha = if (dark) 0.10f else 0.55f),
                shadow = c(main, 0.30f),
            )
        }
    }
}

private fun DrawScope.drawTile(look: Look, glyph: Glyph) {
    val white = Color.White
    drawPath(squircle, Brush.verticalGradient(listOf(look.baseTop, look.baseBottom), 0f, TILE))
    if (look.gloss > 0f) drawPath(squircle, Brush.verticalGradient(listOf(white.copy(alpha = look.gloss), Color.Transparent), 0f, 58f))
    if (look.glow.alpha > 0f) {
        drawPath(squircle, Brush.radialGradient(listOf(look.glow, look.glow.copy(alpha = 0f)), Offset(50f, 104f), 62f))
    }
    // The ring is drawn inset by half its width: centered on the tile's own outline, the part outside the canvas would be cut off at the four extremes
    val inset = (49.6f - look.edgeWidth / 2f) / 49.6f
    withTransform({ scale(inset, inset, pivot = Offset(50f, 50f)) }) { drawPath(squircle, look.edge, style = Stroke(look.edgeWidth)) }
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
        drawPath(body, fill, style = round(look.bodyStroke))
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
    val pick = AccentPalette.pick(colorKey, hue)
    val glyph = AppIcons.glyph(iconName)
    val look = look(style, pick.slot, pick.baseHue, hue, dark)
    Canvas(modifier.size(size)) {
        withTransform({ scale(this@Canvas.size.minDimension / TILE, this@Canvas.size.minDimension / TILE, pivot = Offset.Zero) }) {
            drawTile(look, glyph)
            // Selection: an outline of the tile itself (so it follows the tile shape and look), drawn just outside it.
            if (ringColor != null) withTransform({ scale(1.1f, 1.1f, pivot = Offset(50f, 50f)) }) { drawPath(squircle, ringColor, style = Stroke(5f)) }
        }
    }
}
