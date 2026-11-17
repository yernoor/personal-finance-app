package dev.yernur.moneytracker.data

import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.BillEntity
import dev.yernur.moneytracker.data.db.CategoryEntity
import dev.yernur.moneytracker.data.db.ProjectionMode
import dev.yernur.moneytracker.data.db.ReminderEntity
import dev.yernur.moneytracker.data.db.TransactionEntity
import dev.yernur.moneytracker.data.db.TransactionType
import dev.yernur.moneytracker.data.db.TxFull
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

/** A detected recurring bill with what the owner set for it. */
data class Bill(
    val item: RecurringItem,
    val settings: BillEntity?,
    val category: CategoryEntity?,
    /** Found by its comment inside a category that also holds other spending (internet inside Rent): moving it out is offered. */
    val sharesCategory: Boolean,
) {
    val key get() = item.key
    val reminderOn get() = settings?.reminderOn == true
    val reminderPaused get() = settings?.let { it.reminderOn && it.reminderPaused } == true
    val fixed get() = category?.projectionMode == ProjectionMode.Fixed
    val endMonth: YearMonth? get() = settings?.endMonth?.let(YearMonth::parse)

    /** When the reminder for the next payment goes off; null without one. */
    val reminderAt: LocalDateTime?
        get() = settings?.takeIf { it.reminderOn && !it.reminderPaused }?.let { item.next.minusDays(it.reminderDaysBefore.toLong()).atStartOfDay().plusMinutes(it.reminderMinute.toLong()) }

    /** (paid, total) when an end is set: the payments seen so far, and those plus the ones left up to the end month. */
    val progress: Pair<Int, Int>?
        get() = endMonth?.let { end -> item.months to item.months + (ChronoUnit.MONTHS.between(YearMonth.from(item.next), end).toInt() + 1).coerceAtLeast(0) }
}

/** Detected bills with their settings, biggest first. Bills whose last payment month is before this month have ended and are left out. */
fun bills(rows: List<TxFull>, reminders: List<ReminderEntity>, categories: List<CategoryEntity>, settings: List<BillEntity>, today: LocalDate): List<Bill> {
    val titles = categories.filter { !it.isRemoved }.associate { it.uid to it.title }
    val byUid = categories.associateBy { it.uid }
    val byKey = settings.associateBy { it.key }
    val thisMonth = YearMonth.from(today)
    return detectRecurring(rows, reminders, today, titles).mapNotNull { item ->
        val saved = byKey[item.key]
        if (saved?.endMonth != null && YearMonth.parse(saved.endMonth) < thisMonth) return@mapNotNull null
        val shares = !item.byCategory && item.categoryUid != null && rows.any {
            it.type == TransactionType.Expense && it.categoryUid == item.categoryUid && "comment:${it.comment.orEmpty().trim().lowercase()}" != item.key
        }
        Bill(item, saved, byUid[item.categoryUid], shares)
    }
}

/** A bill's payments in its category: the same comment, and ones without a comment that look like it. */
data class MoveCandidates(val sameComment: List<TxFull>, val lookAlike: List<TxFull>)

/** How close an uncommented payment must be to the bill to be offered: amount within this percent, day of the month within [LOOK_ALIKE_DAYS]. */
const val LOOK_ALIKE_PERCENT = 20
const val LOOK_ALIKE_DAYS = 3

// shortcut: days 30 and 2 count as far apart (no wrap across month ends); fine for bills paid on a fixed day, revisit if one lands on a month end.
fun moveCandidates(bill: RecurringItem, inCategory: List<TxFull>): MoveCandidates {
    val name = bill.title.trim().lowercase()
    val day = bill.next.dayOfMonth
    return MoveCandidates(
        inCategory.filter { it.comment?.trim()?.lowercase() == name },
        inCategory.filter {
            it.comment.isNullOrBlank() && abs(it.amount - bill.amount) * 100 <= bill.amount * LOOK_ALIKE_PERCENT &&
                abs(LocalDate.parse(it.date).dayOfMonth - day) <= LOOK_ALIKE_DAYS
        },
    )
}

/** A finished move: what to say, and the payments as they were (for Undo). */
class MoveDone(val message: String, val before: List<TransactionEntity>)

/** Category -> amount of bills whose last payment was last month: a fixed category's "last month" still holds them, this month will not. */
fun endedLastMonth(settings: List<BillEntity>, today: LocalDate): Map<String, Long> {
    val last = YearMonth.from(today).minusMonths(1).toString()
    return settings.filter { it.endMonth == last && it.categoryUid != null }.groupBy { it.categoryUid!! }.mapValues { (_, l) -> l.sumOf { it.amount } }
}

/** Recurring bills and what the owner sets for them. [changed] runs after any change (it books the next reminder). */
@OptIn(ExperimentalCoroutinesApi::class)
class BillRepository(
    private val db: AppDatabase,
    private val ledger: LedgerRepository,
    private val days: Flow<LocalDate>,
    private val changed: () -> Unit = {},
) {
    private fun from(today: LocalDate) = PeriodUnit.Month.lastRanges(today, RECURRING_MONTHS_BACK).first().from.toString()

    fun observe(): Flow<List<Bill>> = days.flatMapLatest { today ->
        combine(
            db.transactionDao().observeFull(from(today), today.toString()), db.reminderDao().observeAllLive(),
            db.categoryDao().observeAll(), db.billDao().observeAll(),
        ) { rows, reminders, categories, settings -> bills(rows, reminders, categories, settings, today) }
    }

    suspend fun current(today: LocalDate): List<Bill> = bills(
        db.transactionDao().getFull(from(today), today.toString()), db.reminderDao().getLive(), db.categoryDao().getAll(), db.billDao().getAll(), today,
    )

    /** Fixed monthly bill on, or back to Automatic. */
    suspend fun setFixed(bill: Bill, fixed: Boolean) {
        val category = bill.category ?: return
        ledger.saveCategory(category.copy(projectionMode = if (fixed) ProjectionMode.Fixed else ProjectionMode.Auto))
    }

    suspend fun saveReminder(bill: Bill, daysBefore: Int, minuteOfDay: Int) =
        save(base(bill).copy(reminderOn = true, reminderPaused = false, reminderDaysBefore = daysBefore, reminderMinute = minuteOfDay, lastNotified = null))

    /** The switch in More -> Notifications: pauses a reminder without forgetting it. */
    suspend fun setReminderPaused(bill: Bill, paused: Boolean) = save(base(bill).copy(reminderPaused = paused))

    suspend fun removeReminder(bill: Bill) = save(base(bill).copy(reminderOn = false, reminderPaused = false))

    /** Puts back settings saved before (Undo after deleting a reminder). */
    suspend fun restore(settings: BillEntity) = save(settings)

    /** The last payment is [paymentsLeft] payments from now, counting the next one. */
    suspend fun setEnd(bill: Bill, paymentsLeft: Int) =
        save(base(bill).copy(endMonth = YearMonth.from(bill.item.next).plusMonths(paymentsLeft.coerceAtLeast(1) - 1L).toString(), settled = false))

    suspend fun clearEnd(bill: Bill) = save(base(bill).copy(endMonth = null, settled = false))

    suspend fun markNotified(key: String, day: LocalDate) {
        db.billDao().get(key)?.let { db.billDao().upsert(it.copy(lastNotified = day.toString())) }
    }

    /** Acts once on ends that have passed: the reminder stops, and a bill that is its whole category sets that category back to Automatic. */
    suspend fun settleEnded(today: LocalDate) {
        val thisMonth = YearMonth.from(today)
        db.billDao().getAll().filter { it.endMonth != null && !it.settled && YearMonth.parse(it.endMonth) < thisMonth }.forEach { ended ->
            if (ended.key.startsWith("category:")) db.categoryDao().get(ended.key.removePrefix("category:"))
                ?.takeIf { it.projectionMode == ProjectionMode.Fixed }?.let { ledger.saveCategory(it.copy(projectionMode = ProjectionMode.Auto)) }
            db.billDao().upsert(ended.copy(reminderOn = false, settled = true))
        }
        changed()
    }

    suspend fun moveCandidates(bill: Bill): MoveCandidates =
        moveCandidates(bill.item, bill.item.categoryUid?.let { db.transactionDao().getFullInCategory(it) }.orEmpty())

    /** The last move, until undone or dismissed: the Recurring page offers Undo for it after the move screen closes. */
    val lastMove = kotlinx.coroutines.flow.MutableStateFlow<MoveDone?>(null)

    /** Moves the payments to [categoryUid] (named [categoryTitle]); with [addComment], uncommented ones get the bill's name. */
    suspend fun move(bill: Bill, uids: Collection<String>, categoryUid: String, categoryTitle: String, addComment: Boolean) {
        val before = ledger.moveToCategory(uids, categoryUid, bill.item.title.takeIf { addComment })
        lastMove.value = MoveDone("Moved ${before.size} payment${if (before.size == 1) "" else "s"} to $categoryTitle", before)
    }

    suspend fun undo(done: MoveDone) {
        ledger.restore(done.before)
        lastMove.value = null
    }

    /** The saved settings, refreshed with the bill as detected now (title, category, amount). */
    private fun base(bill: Bill) = (bill.settings ?: BillEntity(bill.key, bill.item.title, bill.item.categoryUid, bill.item.amount))
        .copy(title = bill.item.title, categoryUid = bill.item.categoryUid, amount = bill.item.amount)

    private suspend fun save(entity: BillEntity) {
        db.billDao().upsert(entity)
        changed()
    }
}
