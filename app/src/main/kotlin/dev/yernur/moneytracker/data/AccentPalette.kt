package dev.yernur.moneytracker.data

import kotlin.math.absoluteValue
import kotlin.math.pow

/**
 * Category and account colors derived from the accent. The backup stores one of 12 fixed colors per item; each of those
 * is a *slot* here, and a slot's hue is the accent hue plus a fixed step, so the set shifts with the accent but the owner's
 * grouping (same color = same slot) stays. Each hue gets the lightness at which it looks like itself (yellow is light, blue is deep;
 * a dark yellow is just olive), nudged up or down in a three-step cycle so neighbouring hues stay easy to tell apart.
 */
object AccentPalette {
    /**
     * The 12 colors the owner's backup stores for categories and accounts (read from the real file, not guessed), ordered around
     * the hue wheel so the picker walks through the rainbow; the last one is the near-black used for investments.
     */
    val KEYS: List<Int> = listOf(
        0xFFDB4D87, 0xFFF63535, 0xFFE10000, 0xFFF1CC06, 0xFF67AF45, 0xFF8DD66B,
        0xFF9BB68E, 0xFF73B8E1, 0xFF2E78CF, 0xFFB61AED, 0xFFEB5FD6, 0xFF242424,
    ).map { it.toInt() }

    const val SLOTS = 12
    private const val HUE_SLOTS = SLOTS - 1 // the last slot is the neutral dark one
    private const val CHROMA = 0.15f

    fun slotOf(key: Int?): Int = when {
        key == null -> 0
        key in KEYS -> KEYS.indexOf(key)
        else -> key.hashCode().absoluteValue % SLOTS // a color from a newer backup: still a stable slot
    }

    /** Lightness at which a hue looks vivid rather than muddy, interpolated between these (hue, lightness) anchors. */
    private val NATURAL = listOf(0f to 0.62f, 30f to 0.65f, 60f to 0.74f, 90f to 0.84f, 120f to 0.80f, 150f to 0.72f, 180f to 0.68f,
        210f to 0.65f, 240f to 0.58f, 270f to 0.54f, 300f to 0.56f, 330f to 0.60f, 360f to 0.62f)
    private val NUDGE = floatArrayOf(0.06f, 0f, -0.06f)

    private fun naturalLightness(hue: Float): Float {
        val upper = NATURAL.first { it.first >= hue }
        val lower = NATURAL.last { it.first <= hue }
        if (upper.first == lower.first) return upper.second
        return lower.second + (upper.second - lower.second) * (hue - lower.first) / (upper.first - lower.first)
    }

    /** The strong disc color of a slot for an accent of [baseHue] degrees. Dark mode lifts lightness a little. */
    fun main(slot: Int, baseHue: Float, dark: Boolean): Int {
        if (slot == SLOTS - 1) return Oklch.toArgb(if (dark) 0.42f else 0.30f, 0.03f, baseHue)
        val hue = hue(slot, baseHue)
        return Oklch.toArgb(naturalLightness(hue) + NUDGE[slot % NUDGE.size] + if (dark) 0.03f else 0f, CHROMA, hue)
    }

    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val INK = 0xFF1B1B1F.toInt()

    /** White or near-black, whichever contrasts more with the disc (WCAG relative luminance), so the glyph always reads. */
    fun glyphOn(disc: Int): Int = if (contrast(disc, WHITE) >= contrast(disc, INK)) WHITE else INK

    private fun luminance(argb: Int): Double {
        fun channel(v: Int) = (v / 255.0).let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * channel(argb shr 16 and 0xFF) + 0.7152 * channel(argb shr 8 and 0xFF) + 0.0722 * channel(argb and 0xFF)
    }

    private fun contrast(a: Int, b: Int) = (maxOf(luminance(a), luminance(b)) + 0.05) / (minOf(luminance(a), luminance(b)) + 0.05)

    private fun neutral(slot: Int) = slot == SLOTS - 1
    private fun tone(slot: Int, baseHue: Float, lightness: Float, chroma: Float) =
        Oklch.toArgb(lightness, if (neutral(slot)) minOf(chroma, 0.03f) else chroma, hue(slot, baseHue))
    private fun natural(slot: Int, baseHue: Float) = if (neutral(slot)) 0.40f else naturalLightness(hue(slot, baseHue)) + NUDGE[slot % NUDGE.size]

    /** Solid: a gradient tile, lighter at the top. Lightness is capped so the white glyph stays readable (yellow turns gold). */
    private fun springLightness(slot: Int, baseHue: Float, dark: Boolean) = minOf(natural(slot, baseHue), 0.70f) - if (dark) 0.05f else 0f
    fun springTop(slot: Int, baseHue: Float, dark: Boolean) =
        tone(slot, baseHue, if (neutral(slot)) (if (dark) 0.46f else 0.40f) else springLightness(slot, baseHue, dark) + 0.08f, 0.17f)
    fun springBottom(slot: Int, baseHue: Float, dark: Boolean) =
        tone(slot, baseHue, if (neutral(slot)) (if (dark) 0.30f else 0.24f) else springLightness(slot, baseHue, dark) - 0.07f, 0.17f)
    fun springKnock(slot: Int, baseHue: Float, dark: Boolean) = tone(slot, baseHue, if (neutral(slot)) 0.30f else springLightness(slot, baseHue, dark), 0.17f)
    fun springShadow(slot: Int, baseHue: Float, dark: Boolean) = tone(slot, baseHue, maxOf(0.2f, springLightness(slot, baseHue, dark) - 0.28f), 0.13f)

    /** Tonal: light tile (dark tile in dark mode) with a solid glyph of the same hue. */
    fun softBackground(slot: Int, baseHue: Float, dark: Boolean) = tone(slot, baseHue, if (dark) 0.32f else 0.93f, if (dark) 0.06f else 0.045f)
    fun softGlyph(slot: Int, baseHue: Float, dark: Boolean) = tone(slot, baseHue, if (dark) 0.86f else 0.40f, 0.14f)

    /** Liquid tile: translucent tinted glass with a white glyph. */
    private fun liquidLightness(slot: Int, baseHue: Float, dark: Boolean) = minOf(natural(slot, baseHue), 0.66f) - if (dark) 0.07f else 0f
    fun liquidTop(slot: Int, baseHue: Float, dark: Boolean) =
        tone(slot, baseHue, if (neutral(slot)) 0.55f else liquidLightness(slot, baseHue, dark) + 0.08f, 0.16f)
    fun liquidBottom(slot: Int, baseHue: Float, dark: Boolean) =
        tone(slot, baseHue, if (neutral(slot)) 0.32f else liquidLightness(slot, baseHue, dark) - 0.04f, 0.18f)
    fun liquidGlow(slot: Int, baseHue: Float, dark: Boolean) =
        tone(slot, baseHue, if (neutral(slot)) 0.70f else minOf(liquidLightness(slot, baseHue, dark) + 0.22f, 0.90f), 0.15f)
    fun liquidKnock(slot: Int, baseHue: Float, dark: Boolean) = tone(slot, baseHue, maxOf(0.25f, liquidLightness(slot, baseHue, dark) - 0.12f), 0.17f)
    fun liquidShadow(slot: Int, baseHue: Float, dark: Boolean) = tone(slot, baseHue, maxOf(0.2f, liquidLightness(slot, baseHue, dark) - 0.30f), 0.14f)

    /** Clear glass: almost transparent tile; the glyph carries the color as a top-to-bottom gradient. */
    private fun clearLightness(slot: Int, baseHue: Float, dark: Boolean) = if (dark) 0.82f else minOf(natural(slot, baseHue) - 0.02f, 0.58f)
    fun clearGlyphTop(slot: Int, baseHue: Float, dark: Boolean) = tone(slot, baseHue, clearLightness(slot, baseHue, dark) + 0.08f, 0.19f)
    fun clearGlyphBottom(slot: Int, baseHue: Float, dark: Boolean) = tone(slot, baseHue, clearLightness(slot, baseHue, dark) - 0.07f, 0.20f)
    fun clearGlow(slot: Int, baseHue: Float, dark: Boolean) = tone(slot, baseHue, if (dark) 0.70f else 0.82f, 0.14f)
    fun clearKnock(slot: Int, baseHue: Float, dark: Boolean) = tone(slot, baseHue, if (dark) 0.35f else 0.30f, 0.15f)
    fun clearShadow(slot: Int, baseHue: Float, dark: Boolean) = tone(slot, baseHue, if (dark) 0.55f else 0.50f, 0.18f)

    private fun hue(slot: Int, baseHue: Float) = if (slot == SLOTS - 1) baseHue else (baseHue + slot * 360f / HUE_SLOTS) % 360f
}
