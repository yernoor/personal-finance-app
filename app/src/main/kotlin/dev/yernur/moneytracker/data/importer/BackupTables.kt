package dev.yernur.moneytracker.data.importer

typealias Row = Map<String, Any?>

/** Raw rows of the `.mmbackup` SQLite tables the importer needs, as read by [MmbackupReader]. */
class BackupTables(val userVersion: Int, private val tables: Map<String, List<Row>>) {
    fun rows(table: String): List<Row> = tables[table].orEmpty()
}

fun Row.str(column: String): String? = get(column) as? String
fun Row.long(column: String): Long? = (get(column) as? Number)?.toLong()
fun Row.int(column: String): Int? = (get(column) as? Number)?.toInt()
fun Row.double(column: String): Double? = (get(column) as? Number)?.toDouble()
fun Row.bool(column: String): Boolean = (long(column) ?: 0L) != 0L
