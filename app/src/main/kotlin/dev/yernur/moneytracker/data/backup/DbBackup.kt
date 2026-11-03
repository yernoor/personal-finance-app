package dev.yernur.moneytracker.data.backup

import android.database.sqlite.SQLiteDatabase
import dev.yernur.moneytracker.data.db.AppDatabase
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class BackupException(message: String) : Exception(message)

/** What a backup file holds, shown before the owner confirms a restore. */
data class BackupInfo(val schemaVersion: Int, val transactions: Int, val accounts: Int)

/**
 * Copies of the Room database as single SQLite files. [dbFile] is the live database; [autoDir] keeps the copy made before an app update.
 * Not covered: encryption (a backup file is plain data), re-enabling Android's own backup.
 */
class DbBackup(private val db: AppDatabase, private val dbFile: File, private val autoDir: File, private val schemaVersion: Int) {

    /** Writes a consistent single-file copy: `VACUUM INTO` folds the write-ahead log in, so the live files are never copied half-written. */
    fun write(out: OutputStream) {
        val temp = File(autoDir.apply { mkdirs() }, "export.tmp").also { it.delete() }
        try {
            db.openHelper.writableDatabase.execSQL("VACUUM INTO '${temp.path.replace("'", "''")}'")
            temp.inputStream().use { it.copyTo(out) }
        } finally {
            temp.delete()
        }
    }

    /** The copy taken right before an in-app update: the update-failure message points the owner here instead of at "uninstall". */
    fun writeAutoBackup(): File = File(autoDir.apply { mkdirs() }, AUTO_NAME).also { file ->
        file.outputStream().use { write(it) }
    }

    fun autoBackup(): File? = File(autoDir, AUTO_NAME).takeIf { it.exists() }

    /** Copies [source] to a private file and checks it; the caller shows the counts and asks before [restore]. */
    fun stage(source: InputStream): Pair<File, BackupInfo> {
        val staged = File(autoDir.apply { mkdirs() }, "restore.tmp").also { it.delete() }
        source.use { input -> staged.outputStream().use { input.copyTo(it) } }
        return try {
            staged to inspect(staged)
        } catch (e: Exception) {
            staged.delete()
            throw e
        }
    }

    fun inspect(file: File): BackupInfo {
        val header = file.inputStream().use { input -> ByteArray(16).also { input.read(it) } }
        if (String(header, Charsets.US_ASCII) != "SQLite format 3\u0000") throw BackupException("That is not a Money Tracker backup file.")
        val sqlite = try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            throw BackupException("That file could not be opened as a database.")
        }
        sqlite.use {
            if (it.scalar("PRAGMA quick_check") != "ok") throw BackupException("That backup file is damaged.")
            val version = it.scalar("PRAGMA user_version").toInt()
            if (version > schemaVersion) throw BackupException("That backup comes from a newer version of the app. Update the app first.")
            val tables = it.scalar("SELECT COUNT(*) FROM sqlite_master WHERE name IN ('transactions', 'account')")
            if (version < 1 || tables != "2") throw BackupException("That is not a Money Tracker backup file.")
            return BackupInfo(version, it.scalar("SELECT COUNT(*) FROM transactions WHERE isRemoved = 0").toInt(), it.scalar("SELECT COUNT(*) FROM account WHERE isRemoved = 0").toInt())
        }
    }

    /**
     * Replaces the live database with [staged] (already inspected). The caller must restart the app afterwards: this closes the database.
     * The old file stays next to it as `.bak` until the next restore.
     */
    fun restore(staged: File) {
        db.close()
        val bak = File(dbFile.path + ".bak")
        bak.delete()
        dbFile.copyTo(bak)
        listOf("-wal", "-shm").forEach { File(dbFile.path + it).delete() }
        staged.copyTo(dbFile, overwrite = true)
        staged.delete()
    }

    private fun SQLiteDatabase.scalar(sql: String): String = rawQuery(sql, null).use { it.moveToFirst(); it.getString(0) }

    private companion object {
        const val AUTO_NAME = "before-update.db"
    }
}
