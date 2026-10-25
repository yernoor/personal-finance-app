package dev.yernur.moneytracker.data

import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * OKLCH (Ottosson's OKLab, https://bottosson.github.io/posts/oklab/): lightness, chroma and hue that behave the way they look,
 * so colors with the same L and C but different hues feel equally strong. Used to spread category colors around the accent.
 */
object Oklch {
    class Lab(val l: Double, val a: Double, val b: Double) {
        fun distanceTo(other: Lab) = kotlin.math.sqrt((l - other.l).pow(2) + (a - other.a).pow(2) + (b - other.b).pow(2))
    }

    fun lab(argb: Int): Lab {
        val r = decode((argb shr 16 and 0xFF) / 255.0)
        val g = decode((argb shr 8 and 0xFF) / 255.0)
        val b = decode((argb and 0xFF) / 255.0)
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return Lab(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }

    fun hueOf(argb: Int): Float {
        val lab = lab(argb)
        return ((Math.toDegrees(atan2(lab.b, lab.a)) + 360) % 360).toFloat()
    }

    fun chromaOf(argb: Int): Float = lab(argb).let { hypot(it.a, it.b).toFloat() }

    /** Opaque ARGB for the given OKLCH; chroma is reduced until the color fits in sRGB (hue and lightness are kept). */
    fun toArgb(lightness: Float, chroma: Float, hueDegrees: Float): Int {
        var c = chroma.toDouble()
        val h = Math.toRadians(hueDegrees.toDouble())
        while (true) {
            val rgb = linearRgb(lightness.toDouble(), c * cos(h), c * sin(h))
            if (c <= 0.0 || rgb.all { it in -0.0005..1.0005 }) {
                val (r, g, b) = rgb.map { (encode(it.coerceIn(0.0, 1.0)) * 255 + 0.5).toInt() }
                return 0xFF shl 24 or (r shl 16) or (g shl 8) or b
            }
            c -= 0.004
        }
    }

    private fun linearRgb(l: Double, a: Double, b: Double): List<Double> {
        val lp = (l + 0.3963377774 * a + 0.2158037573 * b).pow(3)
        val mp = (l - 0.1055613458 * a - 0.0638541728 * b).pow(3)
        val sp = (l - 0.0894841775 * a - 1.2914855480 * b).pow(3)
        return listOf(
            4.0767416621 * lp - 3.3077115913 * mp + 0.2309699292 * sp,
            -1.2684380046 * lp + 2.6097574011 * mp - 0.3413193965 * sp,
            -0.0041960863 * lp - 0.7034186147 * mp + 1.7076147010 * sp,
        )
    }

    private fun decode(v: Double) = if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    private fun encode(v: Double) = if (v <= 0.0031308) v * 12.92 else 1.055 * v.pow(1 / 2.4) - 0.055
}
