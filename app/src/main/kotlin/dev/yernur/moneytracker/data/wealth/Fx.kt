package dev.yernur.moneytracker.data.wealth

import dev.yernur.moneytracker.data.DEFAULT_CURRENCY
import dev.yernur.moneytracker.data.sheets.HttpRequest
import dev.yernur.moneytracker.data.sheets.HttpTransport
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

class FxException(message: String) : Exception(message)

/**
 * The National Bank of Kazakhstan's official rates (checked 2026-11-04): one XML document per date, `<item>` with `<title>` = currency code,
 * `<description>` = price in KZT and `<quant>` = how many units that price is for (10, 100 or 1000 for some currencies).
 * No key; the owner allows one request a day (see [FxRepository]).
 */
class NbkRatesClient(private val transport: HttpTransport) {
    /** Blocking. KZT per one unit of each currency published for [date]. */
    fun fetch(date: LocalDate): Map<String, Double> {
        val url = "https://nationalbank.kz/rss/get_rates.cfm?fdate=" + date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        val response = transport.execute(HttpRequest("GET", url, emptyMap(), null))
        if (response.code != 200) throw FxException("The National Bank answered with HTTP ${response.code}.")
        val rates = parseNbkRates(response.body)
        if (rates.isEmpty()) throw FxException("The National Bank published no rates for $date.")
        return rates
    }
}

fun parseNbkRates(xml: String): Map<String, Double> {
    // The real feed has no DOCTYPE; refusing one rules out entity tricks in a body from the network without relying on parser features.
    if (xml.contains("<!DOCTYPE", ignoreCase = true)) throw FxException("The rates feed has an unexpected document type.")
    val factory = DocumentBuilderFactory.newInstance()
    val items = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))).getElementsByTagName("item")
    val rates = LinkedHashMap<String, Double>()
    for (i in 0 until items.length) {
        val item = items.item(i) as org.w3c.dom.Element
        fun text(tag: String) = item.getElementsByTagName(tag).item(0)?.textContent?.trim()
        val code = text("title") ?: continue
        val price = text("description")?.toBigDecimalOrNull() ?: continue
        val quant = text("quant")?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: BigDecimal.ONE
        rates[code] = price.divide(quant, 10, RoundingMode.HALF_UP).toDouble()
    }
    return rates
}

/** Converts through KZT (every published rate is against it). Null when either currency has no rate. */
fun convertViaKzt(minor: Long, from: String, to: String, kztPerUnit: Map<String, Double>): Long? {
    if (from == to) return minor
    fun rate(code: String) = if (code == DEFAULT_CURRENCY) BigDecimal.ONE else kztPerUnit[code]?.let { BigDecimal(it.toString()) }
    val fromRate = rate(from) ?: return null
    val toRate = rate(to) ?: return null
    return BigDecimal.valueOf(minor).multiply(fromRate).divide(toRate, 0, RoundingMode.HALF_UP).toLong()
}

/** Currencies the owner is likely to switch between, in this order; only those with a cached rate are offered (KZT always). */
private val PREFERRED_CURRENCIES = listOf("USD", "EUR", "RUB", "GBP", "CNY", "TRY", "AED")

fun currencyChoices(kztPerUnit: Map<String, Double>): List<String> = listOf(DEFAULT_CURRENCY) + PREFERRED_CURRENCIES.filter { it in kztPerUnit }

/** A default-currency amount shown in [currency]; the amount itself when no rate is known (the screen then keeps showing KZT). */
fun fromDefault(minor: Long, currency: String, kztPerUnit: Map<String, Double>): Long = convertViaKzt(minor, DEFAULT_CURRENCY, currency, kztPerUnit) ?: minor

/** The reverse: an amount typed in [currency] stored as default currency. Null without a rate. */
fun toDefault(minor: Long, currency: String, kztPerUnit: Map<String, Double>): Long? = convertViaKzt(minor, currency, DEFAULT_CURRENCY, kztPerUnit)

/**
 * One rate table for the whole app: the backup's pairs, with the National Bank rate winning for every currency it publishes
 * (so Home, Accounts and Wealth show the same tenge for the same account). The reverse pair is dropped, or [dev.yernur.moneytracker.data.Money.convert],
 * which tries the direct pair first, would still use the backup's.
 */
fun mergedRates(backup: Map<Pair<String, String>, Double>, kztPerUnit: Map<String, Double>): Map<Pair<String, String>, Double> {
    val merged = backup.toMutableMap()
    for ((code, rate) in kztPerUnit) {
        merged.remove(DEFAULT_CURRENCY to code)
        merged[code to DEFAULT_CURRENCY] = rate
    }
    return merged
}

/** Converts with a rate table of [mergedRates]: the pair itself when there is one, else through tenge (USD to EUR). Null when a rate is missing. */
fun convertWithRates(minor: Long, from: String, to: String, rates: Map<Pair<String, String>, Double>): Long? =
    dev.yernur.moneytracker.data.Money.convert(minor, from, to, rates)
        ?: dev.yernur.moneytracker.data.Money.convert(minor, from, DEFAULT_CURRENCY, rates)
            ?.let { dev.yernur.moneytracker.data.Money.convert(it, DEFAULT_CURRENCY, to, rates) }
