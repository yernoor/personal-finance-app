package dev.yernur.moneytracker.data.wealth

import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.FxRateEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalDate

/** Caches the National Bank rates in Room and asks the network at most once a day. */
class FxRepository(private val db: AppDatabase, private val settings: AppSettings, private val client: NbkRatesClient?) {
    val rates: Flow<Map<String, Double>> = db.fxRateDao().observeAll().map { rows -> rows.associate { it.currencyCode to it.kztPerUnit } }

    /** The date the cached rates were published for (null: nothing cached yet). */
    suspend fun cachedDate(): String? = db.fxRateDao().getAll().firstOrNull()?.date

    suspend fun convert(minor: Long, from: String, to: String): Long? =
        convertViaKzt(minor, from, to, db.fxRateDao().getAll().associate { it.currencyCode to it.kztPerUnit })

    /**
     * Fetches today's rates unless already asked today. The day is marked once the bank has answered (even with an error or no rates),
     * so a bad response is not retried in a loop; an unreachable network does not use up the day. Returns true when new rates were stored.
     */
    suspend fun refreshDaily(today: LocalDate): Boolean {
        val api = client ?: return false
        if (settings.lastRatesDate.value == today.toString()) return false
        val fetched = try {
            api.fetch(today)
        } catch (e: IOException) {
            return false // offline: try again on the next start
        } catch (e: FxException) {
            settings.setLastRatesDate(today.toString())
            return false
        }
        db.fxRateDao().upsertAll(fetched.map { (code, rate) -> FxRateEntity(code, rate, today.toString()) })
        settings.setLastRatesDate(today.toString())
        return true
    }
}
