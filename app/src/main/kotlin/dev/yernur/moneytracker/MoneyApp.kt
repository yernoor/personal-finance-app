package dev.yernur.moneytracker

import android.app.Application
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.room.Room
import dev.yernur.moneytracker.data.LedgerRepository
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.importer.ImportRepository

/** Hand-wired dependencies: small enough that a DI framework would only add build risk (AGP 9 + KSP). */
class AppContainer(val db: AppDatabase) {
    val ledger = LedgerRepository(db)
    val importer = ImportRepository(db)
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

class MoneyApp : Application() {
    val container by lazy {
        AppContainer(Room.databaseBuilder(this, AppDatabase::class.java, "moneytracker.db").build())
    }
}
