package dev.yernur.moneytracker.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import java.util.Locale

/** The owner's default currency: what `amountInDefaultCurrency` is denominated in (verified from the backup's USD rows). */
const val DEFAULT_CURRENCY = "KZT"

/** Money is Long minor units (x100). BigDecimal appears only here, at the edges. */
object Money {
    private val symbols = mapOf("KZT" to "₸", "USD" to "$", "EUR" to "€", "RUB" to "₽")
    private val format = { pattern: String -> DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)) }

    /** "1 353,70" / "1353.7" -> 135370. Null when not a number. Extra decimals round half-up. */
    fun parseMinor(text: String): Long? {
        val number = text.trim().replace(" ", "").replace(',', '.').toBigDecimalOrNull() ?: return null
        return number.setScale(2, RoundingMode.HALF_UP).movePointRight(2).toLong()
    }

    /** 135370 -> "1,353.70"; whole amounts drop the cents ("1,353"). */
    fun plain(minor: Long): String {
        val amount = BigDecimal.valueOf(minor, 2)
        val whole = minor % 100 == 0L
        return format(if (whole) "#,##0" else "#,##0.00").format(amount)
    }

    fun format(minor: Long, currency: String?): String {
        val symbol = symbols[currency] ?: currency?.let { "$it " }.orEmpty()
        return if (minor < 0) "-$symbol${plain(-minor)}" else "$symbol${plain(minor)}"
    }

    /** Text for an edit field: 135370 -> "1353.7" (no grouping). */
    fun editable(minor: Long): String = BigDecimal.valueOf(minor, 2).stripTrailingZeros().toPlainString()

    /** Converts with a stored rate (direct, else inverse). Null when no rate is known. */
    fun convert(minor: Long, from: String, to: String, rates: Map<Pair<String, String>, Double>): Long? {
        if (from == to) return minor
        val rate = rates[from to to]?.let { BigDecimal(it.toString()) }
            ?: rates[to to from]?.let { BigDecimal.ONE.divide(BigDecimal(it.toString()), 12, RoundingMode.HALF_UP) }
            ?: return null
        return BigDecimal.valueOf(minor).multiply(rate).setScale(0, RoundingMode.HALF_UP).toLong()
    }
}

private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

/** UTC ISO-8601 with millis, the format of `created`/`modified` in the backup. */
fun nowUtc(): String = LocalDateTime.now(ZoneOffset.UTC).format(timestampFormat)
