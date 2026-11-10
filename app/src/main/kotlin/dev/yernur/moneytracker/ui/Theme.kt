package dev.yernur.moneytracker.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import dev.yernur.moneytracker.R

/** Inter for text, Inter Tight for big numbers and headings (both SIL OFL 1.1, licence in assets/fonts; both carry the tenge sign and Cyrillic). */
private fun variable(resId: Int) = FontFamily(
    listOf(400, 500, 600, 700).map { w -> Font(resId, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))) },
)

private val Inter = variable(R.font.inter)
private val InterTight = variable(R.font.inter_tight)

/** Tabular figures, so amounts in a column line up (Inter supports `tnum`). */
private fun TextStyle.inter(family: FontFamily = Inter) = copy(fontFamily = family, fontFeatureSettings = "tnum")

private fun appTypography(base: Typography = Typography()) = base.copy(
    displayLarge = base.displayLarge.inter(InterTight), displayMedium = base.displayMedium.inter(InterTight), displaySmall = base.displaySmall.inter(InterTight),
    headlineLarge = base.headlineLarge.inter(InterTight), headlineMedium = base.headlineMedium.inter(InterTight), headlineSmall = base.headlineSmall.inter(InterTight),
    titleLarge = base.titleLarge.inter(InterTight).copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.inter().copy(fontWeight = FontWeight.SemiBold), titleSmall = base.titleSmall.inter().copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.inter(), bodyMedium = base.bodyMedium.inter(), bodySmall = base.bodySmall.inter(),
    labelLarge = base.labelLarge.inter().copy(fontWeight = FontWeight.Medium), labelMedium = base.labelMedium.inter().copy(fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.inter().copy(fontWeight = FontWeight.Medium),
)

/** System (wallpaper-derived) colors; minSdk 36 so dynamic color is always available. */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = colors, typography = appTypography()) {
        CompositionLocalProvider(LocalIndication provides PressScaleIndication, content = content)
    }
}

/** Over-limit / expense / income colors are fixed on purpose, so they stay recognizable whatever the system accent is. */
object Semantic {
    val income = Color(0xFF2E7D32)
    val expense = Color(0xFFC62828)
}
