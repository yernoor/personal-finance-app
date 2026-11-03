package dev.yernur.moneytracker.data.sheets

import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.google.Authorizer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Thrown when Google wants the owner to approve access again; a background run cannot show that screen. */
class NeedsConsentException : Exception("Google access has to be approved again. Forget the sheet, then Connect Google.")

/**
 * Pull what changed in the sheet (phone edits made since the last sync are kept), then push the phone's data. Used by the button and the
 * weekly job. Runs one at a time: a push clears the sheet before rewriting it, so two at once could leave it empty.
 */
class SyncRunner(private val db: AppDatabase, private val settings: AppSettings, private val transport: HttpTransport) {
    /** Returns what the pull changed, or null on the first sync, which only pushes (see below). */
    suspend fun run(authorizer: Authorizer, onProgress: (String) -> Unit = {}): PullResult? = lock.withLock {
        onProgress("Asking Google for access")
        val token = authorizer.authorize().let { r -> r.accessToken.takeIf { r.consent == null } }
        if (token == null) {
            settings.setGoogleConnected(false)
            throw NeedsConsentException()
        }
        val api = SheetsApi(transport, { refresh -> if (refresh) authorizer.freshToken(token) else token })
        // Without a last-sync time nothing says which side is newer, and a pull would let the old sheet overwrite phone edits: the phone wins once.
        val pulled = if (settings.lastSyncAt.value == null) null else SheetsPull(db, api, settings).pull(onProgress)
        SheetsSync(db, api, settings).pushAll(onProgress)
        pulled
    }

    private companion object {
        val lock = Mutex()
    }
}
