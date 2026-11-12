package dev.yernur.moneytracker.widget

import android.content.Context
import android.content.SharedPreferences
import dev.yernur.moneytracker.data.DEFAULT_CURRENCY
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val MAX_RATE_CURRENCIES = 3

private val DEFAULT_RATE_CURRENCIES = listOf("USD", "EUR", "RUB")

/** Offered first in the picker, in this order; the rest of the cached currencies follow alphabetically. */
private val PREFERRED_RATE_CURRENCIES = listOf("USD", "EUR", "RUB", "GBP", "CNY", "TRY", "AED")

/** One line of the Rates widget: [text] is tenge per one unit of [code], or a dash when no rate is cached. */
data class RateRow(val code: String, val text: String)

/** 453.26 for big rates (cents), five significant digits for small ones (0.01759): the same rule as the converter's rate label. */
fun formatRate(kztPerUnit: Double): String {
    val value = BigDecimal(kztPerUnit.toString())
    val shown = if (value >= BigDecimal(100)) value.setScale(2, RoundingMode.HALF_UP) else value.round(MathContext(5))
    return shown.stripTrailingZeros().toPlainString()
}

fun ratesRows(codes: List<String>, kztPerUnit: Map<String, Double>): List<RateRow> =
    codes.map { RateRow(it, kztPerUnit[it]?.let(::formatRate) ?: "–") }

/** "Rates 09:15" when the cached rates were fetched today, "Rates 12.11" when older, plain "Rates" before the first fetch. */
fun ratesTitle(updatedAt: Long?, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    if (updatedAt == null) return "Rates"
    val at = Instant.ofEpochMilli(updatedAt).atZone(zone)
    return "Rates " + at.format(DateTimeFormatter.ofPattern(if (at.toLocalDate() == today) "HH:mm" else "dd.MM"))
}

/** Currencies the picker offers (never tenge, which every row is quoted in). Before any rate is cached: the usual ones. */
fun rateCurrencyChoices(kztPerUnit: Map<String, Double>): List<String> {
    val cached = kztPerUnit.keys - DEFAULT_CURRENCY
    if (cached.isEmpty()) return PREFERRED_RATE_CURRENCIES
    return PREFERRED_RATE_CURRENCIES.filter { it in cached } + (cached - PREFERRED_RATE_CURRENCIES.toSet()).sorted()
}

/** The currencies chosen for each placed Rates widget, keyed by its appWidgetId (comma-separated, at most [MAX_RATE_CURRENCIES]). */
class RateCurrencies(private val prefs: SharedPreferences) {
    fun get(appWidgetId: Int): List<String> =
        prefs.getString(appWidgetId.toString(), null)?.split(',')?.filter { it.isNotEmpty() }?.take(MAX_RATE_CURRENCIES)?.takeIf { it.isNotEmpty() } ?: DEFAULT_RATE_CURRENCIES

    fun set(appWidgetId: Int, codes: List<String>) = prefs.edit().putString(appWidgetId.toString(), codes.take(MAX_RATE_CURRENCIES).joinToString(",")).apply()

    fun remove(appWidgetId: Int) = prefs.edit().remove(appWidgetId.toString()).apply()

    companion object {
        fun of(context: Context) = RateCurrencies(context.applicationContext.getSharedPreferences("ratewidget", Context.MODE_PRIVATE))
    }
}
