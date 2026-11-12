package dev.yernur.moneytracker.data.wealth

import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.FxRateEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.LocalDate

/** Caches the National Bank rates in Room and asks the network once a day, plus a manual refresh at most once an hour. */
class FxRepository(
    private val db: AppDatabase,
    private val settings: AppSettings,
    private val client: NbkRatesClient?,
    private val now: () -> Long = System::currentTimeMillis,
) {
    val rates: Flow<Map<String, Double>> = db.fxRateDao().observeAll().map { rows -> rows.associate { it.currencyCode to it.kztPerUnit } }

    /** Backup rates overridden by the bank's, for every screen that converts accounts to tenge. */
    val allRates: Flow<Map<Pair<String, String>, Double>> = combine(db.rateDao().observeAll(), rates) { backup, bank ->
        mergedRates(backup.associate { (it.fromCurrencyCode to it.toCurrencyCode) to it.value }, bank)
    }

    private val lock = Mutex()

    /**
     * Fetches today's rates unless already asked today. The day is marked once the bank has answered, whatever the answer (an error, an
     * empty feed, a body that does not parse), so a bad response is not retried in a loop; only an unreachable network leaves the day open.
     * One call at a time, so two screens starting together cannot both ask. Returns true when new rates were stored.
     */
    suspend fun refreshDaily(today: LocalDate): Boolean = lock.withLock {
        val api = client ?: return false
        if (settings.lastRatesDate.value == today.toString()) return false
        val started = now()
        val fetched = try {
            api.fetch(today)
        } catch (e: IOException) {
            return false // offline: try again on the next start
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) { // FxException, or a parser exception on a body that is not the feed
            settings.setLastRatesDate(today.toString())
            settings.setRatesAttemptAt(started)
            return false
        }
        store(fetched, today, started)
        true
    }

    /**
     * The Rates widget's refresh button (owner request 2026-11-12, replacing "one request a day" with "one a day plus a manual one an hour"):
     * asks the bank even if today was already fetched, but not within [cooldownMillis] of the last answer. A bad answer starts the cooldown and
     * leaves the cached rates alone; being offline does not start it. Returns true when new rates were stored.
     */
    suspend fun refreshNow(today: LocalDate, cooldownMillis: Long = HOUR_MILLIS): Boolean = lock.withLock {
        val api = client ?: return false
        val started = now()
        val last = settings.ratesAttemptAt.value
        if (last != null && started - last < cooldownMillis) return false
        val fetched = try {
            api.fetch(today)
        } catch (e: IOException) {
            return false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) { // e.g. the bank has not published today's rates yet
            settings.setRatesAttemptAt(started)
            return false
        }
        store(fetched, today, started)
        true
    }

    private suspend fun store(fetched: Map<String, Double>, today: LocalDate, attemptedAt: Long) {
        db.fxRateDao().upsertAll(fetched.map { (code, rate) -> FxRateEntity(code, rate, today.toString()) })
        settings.setLastRatesDate(today.toString())
        settings.setRatesUpdatedAt(now())
        settings.setRatesAttemptAt(attemptedAt)
    }

    private companion object {
        const val HOUR_MILLIS = 60 * 60 * 1000L
    }
}
