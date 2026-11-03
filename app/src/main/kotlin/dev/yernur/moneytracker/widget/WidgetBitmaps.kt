package dev.yernur.moneytracker.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

/**
 * Glance cannot set a custom font or draw shapes, so the dot-matrix numerals and the bars are drawn into bitmaps.
 * Doto ships in assets/fonts (SIL Open Font License, text next to it).
 */
object WidgetBitmaps {
    /** Palette for category segments: fixed, clearly different hues that read on dark glass and on light cards. */
    val SEGMENTS = intArrayOf(0xFF5BC0BE.toInt(), 0xFFF2B84B.toInt(), 0xFFEF7B6C.toInt(), 0xFF8E9AF0.toInt(), 0xFF7CD38C.toInt(), 0xFFB6B8BC.toInt())

    private fun dotTypeface(context: Context): Typeface =
        runCatching { Typeface.Builder(context.assets, "fonts/doto.ttf").setFontVariationSettings("'wght' 900").build() }
            .getOrNull() ?: Typeface.DEFAULT_BOLD

    /** [text] in Doto at [sizeDp] (always 16 or more: it is a display face), as a bitmap tightly cropped to the text. */
    fun dotText(context: Context, text: String, sizeDp: Float, color: Int): Bitmap {
        val density = context.resources.displayMetrics.density
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; typeface = dotTypeface(context); textSize = sizeDp.coerceAtLeast(16f) * density }
        val width = paint.measureText(text).toInt().coerceAtLeast(1) + 2
        val metrics = paint.fontMetrics
        val bitmap = Bitmap.createBitmap(width, (metrics.descent - metrics.ascent).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawText(text, 1f, -metrics.ascent, paint)
        return bitmap
    }

    /** One bar split into [values] segments, rounded at both ends. Empty or all-zero input gives a plain track. */
    fun stackedBar(values: List<Long>, widthPx: Int = 600, heightPx: Int = 24, track: Int = 0x33FFFFFF): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = heightPx / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = track
        canvas.drawRoundRect(RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat()), radius, radius, paint)
        val total = values.sum().toFloat()
        if (total <= 0f) return bitmap
        val clip = android.graphics.Path().apply { addRoundRect(RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat()), radius, radius, android.graphics.Path.Direction.CW) }
        canvas.clipPath(clip)
        var x = 0f
        values.forEachIndexed { i, value ->
            val w = widthPx * value / total
            paint.color = SEGMENTS[i % SEGMENTS.size]
            canvas.drawRect(x, 0f, x + w, heightPx.toFloat(), paint)
            x += w
        }
        return bitmap
    }

    /** A progress bar: [fraction] 0..1 filled; [color] for the fill. Over 1 is drawn full. */
    fun progressBar(fraction: Float, color: Int, widthPx: Int = 600, heightPx: Int = 16, track: Int = 0x33FFFFFF): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = heightPx / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = track }
        canvas.drawRoundRect(RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat()), radius, radius, paint)
        paint.color = color
        val filled = widthPx * fraction.coerceIn(0f, 1f)
        if (filled > 0f) canvas.drawRoundRect(RectF(0f, 0f, filled.coerceAtLeast(heightPx.toFloat()), heightPx.toFloat()), radius, radius, paint)
        return bitmap
    }
}
