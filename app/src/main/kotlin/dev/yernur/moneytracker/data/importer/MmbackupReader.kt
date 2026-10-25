package dev.yernur.moneytracker.data.importer

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

/** Container: 8 marker bytes, then a ZIP with `MyFinance.db` (docs/FORMAT.md section 1). */
object MmbackupReader {
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private const val DB_ENTRY = "MyFinance.db"

    // "transaction" is an SQL keyword, hence the backticks.
    private val TABLES = listOf("account", "category", "tag", "`transaction`", "transfer", "reminding", "rates", "sync_link")

    fun read(backup: ByteArray, tempDir: File): BackupTables {
        val dbFile = File.createTempFile("mmbackup", ".db", tempDir)
        try {
            dbFile.writeBytes(extractDb(backup))
            val db = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
            try {
                return BackupTables(db.version, TABLES.associate { it.trim('`') to readTable(db, it) })
            } finally {
                db.close()
            }
        } finally {
            dbFile.delete()
        }
    }

    private fun extractDb(backup: ByteArray): ByteArray {
        val start = indexOfZip(backup)
        require(start >= 0) { "No ZIP data found: not a .mmbackup file" }
        ZipInputStream(ByteArrayInputStream(backup, start, backup.size - start)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == DB_ENTRY) return zip.readBytes()
            }
        }
        throw IllegalArgumentException("$DB_ENTRY not found in backup")
    }

    private fun indexOfZip(bytes: ByteArray): Int =
        (0..bytes.size - ZIP_MAGIC.size).firstOrNull { i -> ZIP_MAGIC.indices.all { bytes[i + it] == ZIP_MAGIC[it] } } ?: -1

    private fun readTable(db: SQLiteDatabase, table: String): List<Row> =
        db.rawQuery("SELECT * FROM $table", null).use { cursor ->
            val rows = ArrayList<Row>(cursor.count)
            while (cursor.moveToNext()) {
                rows.add(cursor.columnNames.indices.associate { cursor.columnNames[it] to cursor.value(it) })
            }
            rows
        }

    private fun Cursor.value(i: Int): Any? = when (getType(i)) {
        Cursor.FIELD_TYPE_INTEGER -> getLong(i)
        Cursor.FIELD_TYPE_FLOAT -> getDouble(i)
        Cursor.FIELD_TYPE_STRING -> getString(i)
        else -> null // NULL; BLOBs are not used by the tables we read
    }
}
