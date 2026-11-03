package dev.yernur.moneytracker.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider as DayNightColor
import androidx.glance.unit.ColorProvider
import dev.yernur.moneytracker.MainActivity
import dev.yernur.moneytracker.R

/** Colours of one widget look. Glass is dark and translucent with white text; solid is a card that follows light/dark mode. */
class WidgetPalette(val glass: Boolean) {
    val background = ImageProvider(if (glass) R.drawable.widget_glass else R.drawable.widget_solid)
    val primary = if (glass) ColorProvider(Color.White) else DayNightColor(Color(0xFF14181C), Color(0xFFF2F4F6))
    val secondary = if (glass) ColorProvider(Color(0xCCFFFFFF)) else DayNightColor(Color(0xFF5B636B), Color(0xFFB4BBC2))
    val warning = ColorProvider(Color(0xFFFFB74D))
    val bitmapPrimary = if (glass) 0xFFFFFFFF.toInt() else 0xFF14181C.toInt() // dot numerals cannot follow day/night, so solid uses the dark text
    val bitmapTrack = if (glass) 0x33FFFFFF else 0x22000000
}

/** Opens the app, optionally straight into an add screen (kind) or an existing operation (uid): MainActivity reads the extras. */
fun openApp(context: Context, kind: String? = null, uid: String? = null) = actionStartActivity(
    Intent(context, MainActivity::class.java).apply {
        data = Uri.parse("moneytracker://widget/${kind ?: "app"}/${uid ?: ""}") // makes each PendingIntent distinct
        if (kind != null) putExtra(EXTRA_KIND, kind)
        if (uid != null) putExtra(EXTRA_UID, uid)
    },
)

const val EXTRA_KIND = "widgetKind"
const val EXTRA_UID = "widgetUid"

@Composable
fun WidgetCard(palette: WidgetPalette, onClick: androidx.glance.action.Action, content: @Composable () -> Unit) {
    Box(
        GlanceModifier.fillMaxSize().background(palette.background).cornerRadius(26.dp).padding(14.dp).clickable(onClick),
    ) { content() }
}

@Composable
fun Label(text: String, palette: WidgetPalette, bold: Boolean = false, size: Int = 12, warn: Boolean = false) =
    Text(text, style = TextStyle(color = if (warn) palette.warning else palette.secondary.takeUnless { bold } ?: palette.primary, fontSize = size.sp, fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal), maxLines = 1)

/** The big number: Doto bitmap when enabled (never below 16 sp), otherwise the system font. */
@Composable
fun BigNumber(context: Context, text: String, palette: WidgetPalette, dot: Boolean, sizeDp: Int = 34) {
    if (dot) {
        Image(ImageProvider(WidgetBitmaps.dotText(context, text, sizeDp.toFloat(), palette.bitmapPrimary)), text, GlanceModifier.height((sizeDp * 1.25f).dp))
    } else {
        Text(text, style = TextStyle(color = palette.primary, fontSize = (sizeDp - 4).sp, fontWeight = FontWeight.Medium), maxLines = 1)
    }
}

@Composable
fun BarImage(bitmap: Bitmap, heightDp: Int) =
    Image(ImageProvider(bitmap), null, GlanceModifier.fillMaxWidth().height(heightDp.dp), contentScale = ContentScale.FillBounds)

@Composable
fun LimitRow(bar: LimitBar, palette: WidgetPalette) {
    val fraction = bar.spent.toFloat() / bar.limit
    Column(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Label(bar.title, palette, size = 12)
            Spacer(GlanceModifier.defaultWeight())
            Label("${wholeUnits(bar.spent)} / ${wholeUnits(bar.limit)}", palette, size = 12, warn = fraction > 1f)
        }
        Spacer(GlanceModifier.height(3.dp))
        BarImage(WidgetBitmaps.progressBar(fraction, if (fraction > 1f) 0xFFFFB74D.toInt() else palette.bitmapPrimary, track = palette.bitmapTrack), 6)
    }
}

@Composable
fun OperationRow(context: Context, op: RecentOp, palette: WidgetPalette) {
    Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp).clickable(openApp(context, uid = op.uid)), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            Text(op.title, style = TextStyle(color = palette.primary, fontSize = 13.sp), maxLines = 1)
            if (op.subtitle.isNotEmpty()) Text(op.subtitle, style = TextStyle(color = palette.secondary, fontSize = 11.sp), maxLines = 1)
        }
        Spacer(GlanceModifier.width(8.dp))
        Text((if (op.isIncome) "+" else "") + wholeUnits(op.amount), style = TextStyle(color = palette.primary, fontSize = 13.sp), maxLines = 1)
    }
}
