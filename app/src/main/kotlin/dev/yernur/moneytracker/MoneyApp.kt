package dev.yernur.moneytracker

import android.app.Application
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.room.Room
import dev.yernur.moneytracker.data.AffordRepository
import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.ForecastRepository
import dev.yernur.moneytracker.data.LedgerRepository
import dev.yernur.moneytracker.data.WarningNotifier
import dev.yernur.moneytracker.data.backup.DbBackup
import dev.yernur.moneytracker.data.demo.DemoData
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.DB_VERSION
import dev.yernur.moneytracker.data.db.MIGRATION_1_2
import dev.yernur.moneytracker.data.db.MIGRATION_2_3
import dev.yernur.moneytracker.data.db.MIGRATION_3_4
import dev.yernur.moneytracker.data.db.MIGRATION_4_5
import dev.yernur.moneytracker.data.db.MIGRATION_5_6
import dev.yernur.moneytracker.data.feedback.CrashLog
import dev.yernur.moneytracker.data.importer.ImportRepository
import dev.yernur.moneytracker.data.security.AppLock
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
import kotlinx.coroutines.launch
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
    /** Null in tests that do not need files. */
    val backups: DbBackup? = null,
    /** Redraws the home-screen widgets; a no-op in tests. */
    private val widgetsChanged: suspend () -> Unit = {},
    /** Null in tests: the last crash, kept so the next launch can offer to report it. */
    val crashes: CrashLog? = null,
    /** Demo mode: [db] is the made-up demo database, the owner's real one is not open. Nothing syncs with Google. */
    val demoMode: Boolean = false,
    /** Turns demo mode on or off; the app restarts on the other database. A no-op in tests. */
    val switchDemoMode: (Boolean) -> Unit = {},
) {
    /** The period History is showing; a new entry defaults to its date. Null until History has been opened. */
    val historyRange = kotlinx.coroutines.flow.MutableStateFlow<dev.yernur.moneytracker.data.DateRange?>(null)
    /** A tap on a Home total asks History to open on that side (income or expenses) of the current period; History clears it once it has acted. */
    val historyRequest = kotlinx.coroutines.flow.MutableStateFlow<dev.yernur.moneytracker.ui.HistoryRequest?>(null)
    /** Today when the shown period contains it, otherwise the period's last day (the day itself for a day view). */
    fun newEntryDate(today: LocalDate = LocalDate.now()): LocalDate =
        historyRange.value?.let { if (today in it) today else it.to } ?: today

    val importer = ImportRepository(db)
    val forecasts = ForecastRepository(db, settings)
    val lock = AppLock(settings)
    val fx = FxRepository(db, settings, fxClient)
    val ledger = LedgerRepository(db, allRates = { fx.allRates.first() })
    val wealth = WealthRepository(db, fx)
    val afford = AffordRepository(db, fx, forecasts, wealth)
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

    /** What the app does once on start: yesterday's gap in the history is filled with today's point. Rates first, so a foreign account has its rate. */
    suspend fun dailyWealthRefresh() {
        refreshRatesDaily()
        snapshotNetWorth()
    }

    /** Writes today's net worth snapshot (once per day; later calls the same day refresh it). Silent on failure. */
    suspend fun snapshotNetWorth(today: LocalDate = LocalDate.now()) {
        runCatching { wealth.recordSnapshot(today) }
    }

    /** Call after anything that changes spending: may post a limit warning. */
    suspend fun afterLedgerChange() {
        notifier?.checkAndNotify()
        runCatching { widgetsChanged() }
    }
}

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

class MoneyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        container.crashes?.install()
        dev.yernur.moneytracker.data.sheets.WeeklySyncWorker.schedule(this)
        dev.yernur.moneytracker.data.ReminderWorker.schedule(this, container.settings)
        kotlinx.coroutines.CoroutineScope(Dispatchers.Default).launch { dev.yernur.moneytracker.widget.updateWidgetPreviews(this@MoneyApp) }
    }

    val container by lazy {
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        val demo = getSharedPreferences(MODE_PREFS, MODE_PRIVATE).getBoolean(DEMO_KEY, false)
        val dbName = if (demo) DEMO_DB_NAME else DB_NAME
        val db = Room.databaseBuilder(this, AppDatabase::class.java, dbName).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()
        AppContainer(
            db,
            AppSettings(getSharedPreferences("settings", MODE_PRIVATE)),
            { db, settings, forecasts -> WarningNotifier(this, db, settings, forecasts) },
            UpdateChecker(UrlConnectionTransport(), version), ApkInstaller(this), WorkDownloads(this), version,
            fxClient = NbkRatesClient(UrlConnectionTransport()),
            backups = DbBackup(db, getDatabasePath(dbName), java.io.File(filesDir, if (demo) "demo-backups" else "backups"), DB_VERSION),
            widgetsChanged = { dev.yernur.moneytracker.widget.updateAllWidgets(this) },
            crashes = CrashLog(java.io.File(filesDir, "crash/last-crash.txt")),
            demoMode = demo,
            switchDemoMode = ::switchDemoMode,
        ).also { if (demo) kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { DemoData.seedIfEmpty(db) } }
    }

    /**
     * Records the choice, then restarts into the other database. Turning demo on starts from a fresh demo database (the last demo's is
     * deleted first); the real database file is never opened, changed or deleted by any of this.
     */
    private fun switchDemoMode(on: Boolean) {
        getSharedPreferences(MODE_PREFS, MODE_PRIVATE).edit().putBoolean(DEMO_KEY, on).commit() // commit: it must be on disk before the process ends
        if (on) deleteDatabase(DEMO_DB_NAME) // not open: the app is on the real database right now
        val launch = packageManager.getLaunchIntentForPackage(packageName)!!.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(launch)
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private companion object {
        const val DB_NAME = "moneytracker.db"
        const val DEMO_DB_NAME = "demo.db"
        const val MODE_PREFS = "mode"
        const val DEMO_KEY = "demo"
    }
}
