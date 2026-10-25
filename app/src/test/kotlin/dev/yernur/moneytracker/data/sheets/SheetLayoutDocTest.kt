package dev.yernur.moneytracker.data.sheets

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The layout in code must match the table in docs/SYNC_DESIGN.md, so the doc cannot drift from what the app writes. */
class SheetLayoutDocTest {
    private fun designTable(): Map<String, List<String>> {
        val doc = File("../docs/SYNC_DESIGN.md").readText()
        val row = Regex("""^\|\s*`?(_?\w+)`?(?:\s*\(hidden\))?\s*\|\s*(.+?)\s*\|\s*$""")
        return doc.lines().mapNotNull { row.matchEntire(it) }
            .filter { it.groupValues[1] in SheetLayout.ALL.map { tab -> tab.title } }
            .associate { it.groupValues[1] to it.groupValues[2].split(",").map(String::trim) }
    }

    @Test
    fun everyTabAndColumnOrderMatchesTheDesignDocument() {
        val table = designTable()
        assertEquals(SheetLayout.ALL.map { it.title }.toSet(), table.keys)
        SheetLayout.ALL.forEach { assertEquals("columns of ${it.title}", table.getValue(it.title), it.headers) }
    }

    @Test
    fun transactionsKeepTheConvertersColumnOrder_soExistingFormulasKeepWorking() {
        assertEquals("Date", SheetLayout.TRANSACTIONS.headers.first())
        assertEquals("UID", SheetLayout.TRANSACTIONS.headers.last())
        assertEquals(listOf("Amount", "Currency", "Net (KZT)"), SheetLayout.TRANSACTIONS.headers.subList(5, 8))
        assertTrue(SheetLayout.ALL.filter { it.hidden }.map { it.title }.containsAll(listOf("_deleted", "_meta")))
    }
}
