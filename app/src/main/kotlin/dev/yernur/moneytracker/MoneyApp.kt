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
import dev.yernur.moneytracker.data.importer.ImportRepository

/** Hand-wired dependencies: small enough that a DI framework would only add build risk (AGP 9 + KSP). */
class AppContainer(
    val db: AppDatabase,
    val settings: AppSettings = AppSettings(),
    notifierFor: (AppDatabase, AppSettings, ForecastRepository) -> WarningNotifier? = { _, _, _ -> null },
) {
    val ledger = LedgerRepository(db)
    val importer = ImportRepository(db)
    val forecasts = ForecastRepository(db, settings)
    private val notifier = notifierFor(db, settings, forecasts)

    /** Call after anything that changes spending: may post a limit warning. */
    suspend fun afterLedgerChange() {
        notifier?.checkAndNotify()
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

class MoneyApp : Application() {
    val container by lazy {
        AppContainer(
            Room.databaseBuilder(this, AppDatabase::class.java, "moneytracker.db").addMigrations(MIGRATION_1_2).build(),
            AppSettings(getSharedPreferences("settings", MODE_PRIVATE)),
        ) { db, settings, forecasts -> WarningNotifier(this, db, settings, forecasts) }
    }
}
