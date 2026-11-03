package dev.yernur.moneytracker

import android.app.Application
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.room.Room
import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.ForecastRepository
import dev.yernur.moneytracker.data.LedgerRepository
import dev.yernur.moneytracker.data.WarningNotifier
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.MIGRATION_1_2
import dev.yernur.moneytracker.data.db.MIGRATION_2_3
import dev.yernur.moneytracker.data.db.MIGRATION_3_4
import dev.yernur.moneytracker.data.db.MIGRATION_4_5
import dev.yernur.moneytracker.data.db.MIGRATION_5_6
import dev.yernur.moneytracker.data.importer.ImportRepository
import dev.yernur.moneytracker.data.sheets.UrlConnectionTransport
import dev.yernur.moneytracker.data.update.ApkInstaller
import dev.yernur.moneytracker.data.update.UpdateChecker
import dev.yernur.moneytracker.data.update.UpdateDownloads
import dev.yernur.moneytracker.data.update.UpdateInstaller
import dev.yernur.moneytracker.data.update.WorkDownloads
import dev.yernur.moneytracker.data.wealth.FxRepository
import dev.yernur.moneytracker.data.wealth.NbkRatesClient
import dev.yernur.moneytracker.data.wealth.WealthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Hand-wired dependencies: small enough that a DI framework would only add build risk (AGP 9 + KSP). */
class AppContainer(
    val db: AppDatabase,
    val settings: AppSettings = AppSettings(),
    notifierFor: (AppDatabase, AppSettings, ForecastRepository) -> WarningNotifier? = { _, _, _ -> null },
    /** Null in tests: nothing may reach the network from them. */
    val updateChecker: UpdateChecker? = null,
    val updateInstaller: UpdateInstaller? = null,
    val updateDownloads: UpdateDownloads? = null,
    val installedVersion: String = "0.0.0",
    /** Null in tests, like [updateChecker]. */
    fxClient: NbkRatesClient? = null,
) {
    val importer = ImportRepository(db)
    val forecasts = ForecastRepository(db, settings)
    val fx = FxRepository(db, settings, fxClient)
    val ledger = LedgerRepository(db, allRates = { fx.allRates.first() })
    val wealth = WealthRepository(db, fx)
    private val notifier = notifierFor(db, settings, forecasts)

    /** Asks GitHub for a newer release, but at most once a day; a failure is silent (the Updates screen shows errors when asked by hand). */
    suspend fun checkForUpdateDaily(now: Long = System.currentTimeMillis()) {
        val checker = updateChecker ?: return
        if (now - settings.lastUpdateCheck.value < DAY_MILLIS) return
        settings.setLastUpdateCheck(now)
        runCatching { withContext(Dispatchers.IO) { checker.check() } }.onSuccess { settings.setAvailableUpdate(it?.tag) }
    }

    /** Fetches the National Bank rates, at most once a day; failures are silent (the converter keeps using the cached rates). */
    suspend fun refreshRatesDaily(today: LocalDate = LocalDate.now()) {
        runCatching { withContext(Dispatchers.IO) { fx.refreshDaily(today) } }
    }

    /** Writes today's net worth snapshot (once per day; later calls the same day refresh it). Silent on failure. */
    suspend fun snapshotNetWorth(today: LocalDate = LocalDate.now()) {
        runCatching { wealth.recordSnapshot(today) }
    }

    /** Call after anything that changes spending: may post a limit warning. */
    suspend fun afterLedgerChange() {
        notifier?.checkAndNotify()
    }
}

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

class MoneyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        dev.yernur.moneytracker.data.sheets.WeeklySyncWorker.schedule(this)
        dev.yernur.moneytracker.data.ReminderWorker.schedule(this, container.settings)
    }

    val container by lazy {
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        AppContainer(
            Room.databaseBuilder(this, AppDatabase::class.java, "moneytracker.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build(),
            AppSettings(getSharedPreferences("settings", MODE_PRIVATE)),
            { db, settings, forecasts -> WarningNotifier(this, db, settings, forecasts) },
            UpdateChecker(UrlConnectionTransport(), version), ApkInstaller(this), WorkDownloads(this), version,
            NbkRatesClient(UrlConnectionTransport()),
        )
    }
}
