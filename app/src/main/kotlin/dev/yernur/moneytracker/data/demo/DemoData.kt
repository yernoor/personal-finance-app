package dev.yernur.moneytracker.data.demo

import dev.yernur.moneytracker.data.db.AccountEntity
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.BondEntity
import dev.yernur.moneytracker.data.db.CashflowItemEntity
import dev.yernur.moneytracker.data.db.CashflowKind
import dev.yernur.moneytracker.data.db.CategoryEntity
import dev.yernur.moneytracker.data.db.ManualAssetEntity
import dev.yernur.moneytracker.data.db.ManualKind
import dev.yernur.moneytracker.data.db.TransactionEntity
import dev.yernur.moneytracker.data.db.TransactionType
import dev.yernur.moneytracker.data.db.TransferEntity
import java.time.LocalDate
import java.util.Random
import java.util.UUID
import kotlinx.coroutines.flow.first

/**
 * Made-up data for demo mode: four months of everyday spending, two salaries a month, and transfers both ways between the same accounts
 * (so History's netting shows), plus a few wealth entries (two bonds, two other assets, monthly income and payments). Nothing here comes from the owner's backup. Fixed seed, dated back from [today], so every demo looks alike.
 */
object DemoData {
    /** Used for the dollar account's default-currency amounts (a demo constant, not a real rate). */
    const val USD_RATE = 480L

    private data class Spend(val uid: String, val title: String, val icon: String, val color: Int, val everyDays: Int, val min: Long, val max: Long, val limit: Long? = null)

    private val spending = listOf(
        Spend("demo-groceries", "Groceries", "products", 0xFF43A047.toInt(), 2, 3_000, 15_000, 120_000),
        Spend("demo-cafe", "Cafe", "cafe", 0xFFE57373.toInt(), 3, 2_500, 9_000, 60_000),
        Spend("demo-transport", "Transport", "transport", 0xFF26A69A.toInt(), 1, 400, 1_800, 40_000),
        Spend("demo-health", "Health", "heart", 0xFF7E57C2.toInt(), 17, 4_000, 25_000),
        Spend("demo-sport", "Sport", "sport", 0xFFFF8A65.toInt(), 9, 2_000, 6_000),
        Spend("demo-clothes", "Clothes", "clothes", 0xFF5C6BC0.toInt(), 23, 8_000, 40_000),
    )

    /** Fills [db] when it has no accounts yet; a demo database the owner has already used is left as it is. */
    suspend fun seedIfEmpty(db: AppDatabase, today: LocalDate = LocalDate.now()) {
        if (db.accountDao().observeAll().first().isNotEmpty()) return
        val random = Random(42)
        fun uid() = UUID(random.nextLong(), random.nextLong()).toString()
        fun stamp(date: LocalDate) = "${date}T12:00:00.000Z"
        val first = today.minusDays(120)
        val created = stamp(first)

        db.accountDao().insertAll(
            listOf(
                AccountEntity("main", "Everyday card", "KZT", "bank", 0xFF42A5F5.toInt(), 0, null, false, true, false, false, created, created),
                AccountEntity("demo-savings", "Savings", "KZT", "piggy_bank", 0xFF66BB6A.toInt(), 1, null, true, true, false, false, created, created),
                AccountEntity("demo-cash", "Cash", "KZT", "cash", 0xFFFFA726.toInt(), 2, null, false, true, false, false, created, created),
                AccountEntity("demo-usd", "Dollars", "USD", "dollar", 0xFFAB47BC.toInt(), 3, null, true, true, false, false, created, created),
            )
        )
        val rent = Spend("demo-rent", "Rent", "apartment", 0xFF8D6E63.toInt(), 0, 0, 0)
        val phone = Spend("demo-phone", "Phone", "mobile", 0xFF78909C.toInt(), 0, 0, 0)
        val expenseCategories = (spending + rent + phone).mapIndexed { i, s ->
            CategoryEntity(s.uid, s.title, TransactionType.Expense, s.icon, s.color, 0, null, s.limit?.times(100), i, false, created, created)
        }
        val incomeCategories = listOf(
            CategoryEntity("demo-salary", "Salary", TransactionType.Income, "salary", 0xFF2E7D32.toInt(), 0, null, null, 0, false, created, created),
            CategoryEntity("demo-bonus", "Bonus", TransactionType.Income, "bonus", 0xFFF9A825.toInt(), 0, null, null, 1, false, created, created),
        )
        db.categoryDao().insertAll(expenseCategories + incomeCategories)

        val transactions = mutableListOf<TransactionEntity>()
        fun add(type: TransactionType, account: String, category: String?, amount: Long, date: LocalDate, currency: String = "KZT") {
            val minor = amount * 100
            val default = if (currency == "USD") minor * USD_RATE else minor
            transactions += TransactionEntity(uid(), type, account, category, default, minor, currency, minor, currency, date.toString(), null, stamp(date), stamp(date))
        }
        // Opening balances, as balance corrections (no category), like the ones the importer brings in.
        add(TransactionType.Income, "main", null, 350_000, first)
        add(TransactionType.Income, "demo-savings", null, 1_200_000, first)
        add(TransactionType.Income, "demo-cash", null, 30_000, first)
        add(TransactionType.Income, "demo-usd", null, 800, first, "USD")

        val transfers = mutableListOf<TransferEntity>()
        fun move(from: String, to: String, sent: Long, date: LocalDate, received: Long? = null, fromCurrency: String = "KZT", toCurrency: String = "KZT") {
            transfers += TransferEntity(uid(), from, to, sent * 100, fromCurrency, received?.times(100), if (received != null) toCurrency else null, date.toString(), null, stamp(date), stamp(date))
        }

        var day = first.plusDays(1)
        var index = 0
        while (!day.isAfter(today)) {
            spending.forEach { s ->
                if (index % s.everyDays == 0) {
                    val account = if (s.uid == "demo-transport" && random.nextInt(4) == 0) "demo-cash" else "main"
                    val amount = (s.min + random.nextInt((s.max - s.min).toInt())) / 100 * 100
                    add(TransactionType.Expense, account, s.uid, amount, day)
                }
            }
            when (day.dayOfMonth) {
                1 -> add(TransactionType.Expense, "main", rent.uid, 180_000, day)
                3 -> move("main", "demo-cash", 20_000, day)
                5 -> add(TransactionType.Expense, "main", phone.uid, 5_500, day)
                10, 25 -> add(TransactionType.Income, "main", "demo-salary", 420_000, day)
                11 -> move("main", "demo-savings", 150_000, day)
                12 -> move("main", "demo-usd", 96_000, day, received = 200, toCurrency = "USD")
                20 -> move("demo-savings", "main", 40_000, day)
                22 -> move("demo-usd", "main", 50, day, received = 24_000, fromCurrency = "USD")
                26 -> if (day.monthValue % 3 == 0) add(TransactionType.Income, "main", "demo-bonus", 150_000, day)
            }
            day = day.plusDays(1)
            index++
        }
        db.transactionDao().insertAll(transactions)
        db.transferDao().insertAll(transfers)
        seedWealth(db, created)
    }

    /** Amounts are minor units (x100). The monthly salary matches the two 420,000 paydays above. */
    private suspend fun seedWealth(db: AppDatabase, stamp: String) {
        db.manualAssetDao().upsert(ManualAssetEntity("demo-asset-flat", "Flat", ManualKind.Asset, "KZT", 30_000_000_00, stamp, stamp))
        db.manualAssetDao().upsert(ManualAssetEntity("demo-asset-car", "Car", ManualKind.Asset, "KZT", 5_000_000_00, stamp, stamp))
        db.bondDao().upsert(BondEntity("demo-bond-usd", "Treasury bond 2029", "USD", 1_000_00, 2, 1_960_00, 425, 2, "2029-08-15", 2_010_00, stamp, stamp))
        db.bondDao().upsert(BondEntity("demo-bond-kzt", "Corporate bond 2030", "KZT", 100_000_00, 5, 480_000_00, 1400, 2, "2030-03-10", null, stamp, stamp))
        db.cashflowItemDao().upsert(CashflowItemEntity("demo-cf-salary", "Salary", CashflowKind.Income, "KZT", 840_000_00, false, stamp, stamp))
        db.cashflowItemDao().upsert(CashflowItemEntity("demo-cf-rent", "Rent", CashflowKind.Obligation, "KZT", 180_000_00, true, stamp, stamp))
        db.cashflowItemDao().upsert(CashflowItemEntity("demo-cf-subs", "Subscriptions", CashflowKind.Obligation, "KZT", 9_000_00, false, stamp, stamp))
    }
}
