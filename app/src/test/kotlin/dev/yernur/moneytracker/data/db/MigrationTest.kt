package dev.yernur.moneytracker.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private fun SupportSQLiteDatabase.scalar(sql: String): String = query(sql).use { it.moveToFirst(); it.getString(0) }

    @Test
    fun migrate1To2_keepsCategories_andFillsNewColumnsWithDefaults() {
        helper.createDatabase(DB, 1).use { v1 ->
            v1.execSQL(
                "INSERT INTO category (uid, title, type, icon, color, useCount, lastUse, limitAmount, position, isArchived, created, modified, isRemoved) " +
                    "VALUES ('c1', 'Досуг', 'Expense', 'wallet', 5, 3, NULL, 5000000, 0, 0, 't', 't', 0)"
            )
        }
        val v2 = helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2)
        assertEquals("Досуг", v2.scalar("SELECT title FROM category WHERE uid = 'c1'"))
        assertEquals("5000000", v2.scalar("SELECT limitAmount FROM category WHERE uid = 'c1'"))
        assertEquals("Auto", v2.scalar("SELECT projectionMode FROM category WHERE uid = 'c1'"))
        assertTrue(v2.query("SELECT warnThresholdPercent FROM category").use { it.moveToFirst(); it.isNull(0) })
    }

    @Test
    fun migrate2To3_addsAnEmptyFxRateTable() {
        helper.createDatabase(DB, 2).close()
        val v3 = helper.runMigrationsAndValidate(DB, 3, true, MIGRATION_2_3)
        assertEquals("0", v3.scalar("SELECT COUNT(*) FROM fx_rate"))
    }

    @Test
    fun migrate3To4_addsEmptyWealthTables() {
        helper.createDatabase(DB, 3).close()
        val v4 = helper.runMigrationsAndValidate(DB, 4, true, MIGRATION_3_4)
        for (table in listOf("manual_asset", "net_worth_snapshot", "goal")) assertEquals("0", v4.scalar("SELECT COUNT(*) FROM $table"))
    }

    @Test
    fun migrate4To5_addsBonds_andKeepsSnapshotsWithZeroInvestments() {
        helper.createDatabase(DB, 4).use { v4 ->
            v4.execSQL("INSERT INTO net_worth_snapshot (date, accountsMinor, assetsMinor, liabilitiesMinor) VALUES ('2026-11-04', 100, 50, 20)")
        }
        val v5 = helper.runMigrationsAndValidate(DB, 5, true, MIGRATION_4_5)
        assertEquals("0", v5.scalar("SELECT COUNT(*) FROM bond"))
        assertEquals("0", v5.scalar("SELECT investmentsMinor FROM net_worth_snapshot WHERE date = '2026-11-04'"))
    }

    private companion object {
        const val DB = "migration-test"
    }
}
