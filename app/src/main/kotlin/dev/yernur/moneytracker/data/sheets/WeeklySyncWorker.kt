package dev.yernur.moneytracker.data.sheets

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.yernur.moneytracker.MoneyApp
import dev.yernur.moneytracker.data.google.GoogleAuthorizer
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import java.util.concurrent.TimeUnit

/** Runs [SyncRunner] about once a week while online, when a sheet is linked and auto sync is on. Failures are silent; the Sheets screen shows the last sync time. */
class WeeklySyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as MoneyApp).container
        val settings = container.settings
        if (container.demoMode || settings.spreadsheetId.value == null || !settings.autoSync.value) return Result.success() // demo data never reaches the sheet
        return try {
            SyncRunner(container.db, settings, UrlConnectionTransport()).run(GoogleAuthorizer(applicationContext))
            Result.success()
        } catch (e: IOException) {
            Result.retry() // offline or Google unreachable
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure() // consent revoked, sheet gone, ...: wait for next week
        }
    }

    companion object {
        private const val NAME = "weekly-sheets-sync"
        private const val OLD_DAILY_NAME = "daily-sheets-sync" // an earlier build of this app scheduled it daily

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeeklySyncWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            // Best effort: unit tests (Robolectric) have no WorkManager initializer, and a missing schedule must never stop the app.
            runCatching {
                val work = WorkManager.getInstance(context)
                work.cancelUniqueWork(OLD_DAILY_NAME)
                work.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            }
        }
    }
}
