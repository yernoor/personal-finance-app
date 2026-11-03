package dev.yernur.moneytracker.data.wealth

import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.FxRateEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.LocalDate

/** Caches the National Bank rates in Room and asks the network at most once a day. */
class FxRepository(private val db: AppDatabase, private val settings: AppSettings, private val client: NbkRatesClient?) {
    val rates: Flow<Map<String, Double>> = db.fxRateDao().observeAll().map { rows -> rows.associate { it.currencyCode to it.kztPerUnit } }

    /** Backup rates overridden by the bank's, for every screen that converts accounts to tenge. */
    val allRates: Flow<Map<Pair<String, String>, Double>> = combine(db.rateDao().observeAll(), rates) { backup, bank ->
        mergedRates(backup.associate { (it.fromCurrencyCode to it.toCurrencyCode) to it.value }, bank)
    }

    /** The date the cached rates were published for (null: nothing cached yet). */
    suspend fun cachedDate(): String? = db.fxRateDao().getAll().firstOrNull()?.date

    suspend fun convert(minor: Long, from: String, to: String): Long? =
        convertViaKzt(minor, from, to, db.fxRateDao().getAll().associate { it.currencyCode to it.kztPerUnit })

    private val lock = Mutex()

    /**
     * Fetches today's rates unless already asked today. The day is marked once the bank has answered, whatever the answer (an error, an
     * empty feed, a body that does not parse), so a bad response is not retried in a loop; only an unreachable network leaves the day open.
     * One call at a time, so two screens starting together cannot both ask. Returns true when new rates were stored.
     */
    suspend fun refreshDaily(today: LocalDate): Boolean = lock.withLock {
        val api = client ?: return false
        if (settings.lastRatesDate.value == today.toString()) return false
        val fetched = try {
            api.fetch(today)
        } catch (e: IOException) {
            return false // offline: try again on the next start
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) { // FxException, or a parser exception on a body that is not the feed
            settings.setLastRatesDate(today.toString())
            return false
        }
        db.fxRateDao().upsertAll(fetched.map { (code, rate) -> FxRateEntity(code, rate, today.toString()) })
        settings.setLastRatesDate(today.toString())
        true
    }
}
