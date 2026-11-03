package dev.yernur.moneytracker.data

import kotlin.math.abs
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentPaletteTest {
    private fun hexOf(argb: Int) = "%06X".format(argb and 0xFFFFFF)

    @Test
    fun oklch_matchesKnownReferenceColors() {
        // Reference values from Ottosson's OKLab article: pure red, green and blue
        assertEquals(29.23, Oklch.hueOf(0xFFFF0000.toInt()).toDouble(), 0.1)
        assertEquals(142.5, Oklch.hueOf(0xFF00FF00.toInt()).toDouble(), 0.1)
        assertEquals(264.05, Oklch.hueOf(0xFF0000FF.toInt()).toDouble(), 0.1)
        assertEquals(0.628, Oklch.lab(0xFFFF0000.toInt()).l, 0.001)
        assertEquals(1.0, Oklch.lab(0xFFFFFFFF.toInt()).l, 0.001)
    }

    @Test
    fun toArgb_roundTripsAndStaysInGamut() {
        val argb = Oklch.toArgb(0.62f, 0.14f, 250f)
        assertEquals(250.0, Oklch.hueOf(argb).toDouble(), 2.0)
        assertEquals(0.62, Oklch.lab(argb).l, 0.01)
        assertEquals(0xFF, argb ushr 24)
        // An impossible chroma is pulled back into sRGB instead of overflowing
        val clipped = Oklch.toArgb(0.9f, 0.5f, 140f)
        assertTrue(Oklch.chromaOf(clipped) < 0.4f)
    }

    @Test
    fun slots_followThePickerOrder_andUnknownColorsGetAStableSlot() {
        AccentPalette.KEYS.forEachIndexed { index, key -> assertEquals(index, AccentPalette.slotOf(key)) }
        assertEquals(0, AccentPalette.slotOf(null))
        val unknown = 0xFF123456.toInt()
        assertEquals(AccentPalette.slotOf(unknown), AccentPalette.slotOf(unknown))
        assertTrue(AccentPalette.slotOf(unknown) in 0 until AccentPalette.SLOTS)
    }

    @Test
    fun theTwelveColors_areClearlyDistinct_forAnyAccent_inLightAndDark() {
        for (dark in listOf(false, true)) {
            for (baseHue in 0..330 step 30) {
                val labs = (0 until AccentPalette.SLOTS).map { Oklch.lab(AccentPalette.main(it, baseHue.toFloat(), dark)) }
                var closest = Double.MAX_VALUE
                for (i in labs.indices) for (j in i + 1 until labs.size) closest = minOf(closest, labs[i].distanceTo(labs[j]))
                assertTrue("accent hue $baseHue dark=$dark: closest pair is only $closest apart", closest >= 0.07)
            }
        }
    }

    @Test
    fun theColorsAreNotMuted_exceptTheNeutralDarkOne() {
        for (slot in 0 until AccentPalette.SLOTS - 1) {
            val chroma = Oklch.chromaOf(AccentPalette.main(slot, 265f, dark = false))
            assertTrue("slot $slot chroma $chroma", chroma >= 0.08f) // sRGB caps teal/cyan lower than red or violet
        }
        assertTrue(Oklch.chromaOf(AccentPalette.main(AccentPalette.SLOTS - 1, 265f, false)) < 0.06f)
    }

    @Test
    fun theWholeSetShiftsWithTheAccent() {
        val blue = AccentPalette.main(4, 265f, false)
        val red = AccentPalette.main(4, 25f, false)
        assertTrue("same slot, different accent: ${hexOf(blue)} vs ${hexOf(red)}", abs(Oklch.hueOf(blue) - Oklch.hueOf(red)) > 100f)
    }

    private fun luminance(argb: Int): Double {
        fun channel(v: Int) = (v / 255.0).let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * channel(argb shr 16 and 0xFF) + 0.7152 * channel(argb shr 8 and 0xFF) + 0.0722 * channel(argb and 0xFF)
    }

    private fun contrast(a: Int, b: Int) = (maxOf(luminance(a), luminance(b)) + 0.05) / (minOf(luminance(a), luminance(b)) + 0.05)

    @Test
    fun glyphsAlwaysReadOnTheirDisc() {
        for (dark in listOf(false, true)) for (baseHue in 0..330 step 30) for (slot in 0 until AccentPalette.SLOTS) {
            val disc = AccentPalette.main(slot, baseHue.toFloat(), dark)
            val ratio = contrast(disc, AccentPalette.glyphOn(disc))
            assertTrue("slot $slot accent $baseHue dark=$dark: contrast $ratio", ratio >= 4.0) // the WCAG minimum for graphics is 3:1; with an off-black ink the worst case is about 4.15
        }
    }
}
