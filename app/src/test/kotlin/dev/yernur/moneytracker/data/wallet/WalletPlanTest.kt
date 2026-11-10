package dev.yernur.moneytracker.data.wallet

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.data.LedgerRepository
import dev.yernur.moneytracker.data.db.AccountEntity
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.CategoryEntity
import dev.yernur.moneytracker.data.db.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class WalletPlanTest {
    private lateinit var db: AppDatabase
    private val payment = WalletPayment("KZT", 170_000, "SOME SHOP", "Test Gold", "0000")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun account(uid: String, currency: String = "KZT", archived: Boolean = false) =
        AccountEntity(uid, uid, currency, null, null, null, null, false, true, archived, false, "", "")

    private fun category(uid: String, uses: Int, type: TransactionType = TransactionType.Expense, archived: Boolean = false, position: Int? = 0) =
        CategoryEntity(uid, uid, type, null, null, uses, null, null, position, archived, "", "")

    @Test
    fun noDefaultAccount_asksWhich_andAMissingOneAsksAgain() {
        val accounts = listOf(account("main"))
        assertEquals(WalletStep.AskAccount, WalletPlan.decide(payment, accounts, null))
        assertEquals(WalletStep.AskAccount, WalletPlan.decide(payment, accounts, "deleted-account"))
    }

    @Test
    fun sameCurrency_asksTheCategory_otherCurrencyIsLeftToTheOwner() {
        val kzt = account("main")
        val usd = account("usd", "USD")
        assertEquals(WalletStep.AskCategory(kzt), WalletPlan.decide(payment, listOf(kzt, usd), "main"))
        assertEquals(WalletStep.CurrencyMismatch(usd), WalletPlan.decide(payment, listOf(kzt, usd), "usd"))
    }

    @Test
    fun archivedAccountsAreNotOffered() {
        assertEquals(listOf("a"), WalletPlan.choosableAccounts(listOf(account("a"), account("b", archived = true))).map { it.uid })
    }

    @Test
    fun categoriesAreExpenseOnly_notArchived_mostUsedFirst() {
        val all = listOf(
            category("rare", 1), category("often", 9), category("salary", 50, TransactionType.Income),
            category("old", 99, archived = true), category("tie-b", 5, position = 2), category("tie-a", 5, position = 1),
        )
        assertEquals(listOf("often", "tie-a", "tie-b", "rare"), WalletPlan.expenseCategories(all).map { it.uid })
    }

    @Test
    fun dateIsTheLocalDayOfThePost() {
        val postTime = 1_794_304_844_485L // 2026-11-10 10:00 UTC
        assertEquals("2026-11-10", WalletPlan.localDate(postTime, ZoneId.of("UTC")))
        assertEquals("2026-11-10", WalletPlan.localDate(postTime, ZoneId.of("Asia/Almaty")))
        assertEquals("2026-11-11", WalletPlan.localDate(1_794_351_600_000L, ZoneId.of("Asia/Almaty"))) // 2026-11-10 23:00 UTC is already the 11th at UTC+5
    }

    @Test
    fun savingCreatesTheExpense_withTheMerchantAsComment_andBumpsTheCategory() = runBlocking {
        val ledger = LedgerRepository(db)
        ledger.saveAccount(account("main"))
        db.categoryDao().insertAll(listOf(category("cafe", 0)))

        WalletPlan.save(ledger, PendingPayment(payment, 1_794_304_844_485L), "main", "cafe", ZoneId.of("UTC"))

        val saved = db.transactionDao().getAll().single()
        assertEquals(TransactionType.Expense, saved.type)
        assertEquals("main", saved.accountUid)
        assertEquals("cafe", saved.categoryUid)
        assertEquals(170_000L, saved.amountInAccountCurrency)
        assertEquals(170_000L, saved.amountInDefaultCurrency) // KZT is the default currency
        assertEquals("2026-11-10", saved.date)
        assertEquals("SOME SHOP", saved.comment)
        assertEquals(1, db.categoryDao().get("cafe")!!.useCount)
        assertTrue(!saved.isRemoved)
    }
}
