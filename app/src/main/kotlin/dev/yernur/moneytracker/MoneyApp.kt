package dev.yernur.moneytracker

import android.app.Application
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.room.Room
import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.ForecastRepository
import dev.yernur.moneytracker.data.LedgerRepository
import dev.yernur.moneytracker.data.WarningNotifier
import dev.yernur.moneytracker.data.backup.DbBackup
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.MIGRATION_1_2
import dev.yernur.moneytracker.data.importer.ImportRepository
import dev.yernur.moneytracker.data.security.AppLock
import dev.yernur.moneytracker.data.sheets.UrlConnectionTransport
import dev.yernur.moneytracker.data.update.ApkInstaller
import dev.yernur.moneytracker.data.update.UpdateChecker
import dev.yernur.moneytracker.data.update.UpdateDownloads
import dev.yernur.moneytracker.data.update.UpdateInstaller
import dev.yernur.moneytracker.data.update.WorkDownloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    /** Null in tests that do not need files. */
    val backups: DbBackup? = null,
) {
    val ledger = LedgerRepository(db)
    val importer = ImportRepository(db)
    val forecasts = ForecastRepository(db, settings)
    val lock = AppLock(settings)
    private val notifier = notifierFor(db, settings, forecasts)

    /** Asks GitHub for a newer release, but at most once a day; a failure is silent (the Updates screen shows errors when asked by hand). */
    suspend fun checkForUpdateDaily(now: Long = System.currentTimeMillis()) {
        val checker = updateChecker ?: return
        if (now - settings.lastUpdateCheck.value < DAY_MILLIS) return
        settings.setLastUpdateCheck(now)
        runCatching { withContext(Dispatchers.IO) { checker.check() } }.onSuccess { settings.setAvailableUpdate(it?.tag) }
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
        val db = Room.databaseBuilder(this, AppDatabase::class.java, DB_NAME).addMigrations(MIGRATION_1_2).build()
        AppContainer(
            db,
            AppSettings(getSharedPreferences("settings", MODE_PRIVATE)),
            { db, settings, forecasts -> WarningNotifier(this, db, settings, forecasts) },
            UpdateChecker(UrlConnectionTransport(), version), ApkInstaller(this), WorkDownloads(this), version,
            DbBackup(db, getDatabasePath(DB_NAME), java.io.File(filesDir, "backups"), DB_VERSION),
        )
    }

    private companion object {
        const val DB_NAME = "moneytracker.db"
        const val DB_VERSION = 2 // keep equal to @Database(version) in AppDatabase
    }
}
