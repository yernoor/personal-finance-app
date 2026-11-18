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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.sync.withLock

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

/**
 * Detected bills and the ones added by hand ([BillEntity.manual], see [manualRecurring]), with their settings, biggest first. Bills whose end
 * month is before this month have ended and are left out. A category found as a bill is dropped when a hand-added bill lives in it (its payments).
 */
fun bills(rows: List<TxFull>, reminders: List<ReminderEntity>, categories: List<CategoryEntity>, settings: List<BillEntity>, today: LocalDate): List<Bill> {
    val titles = categories.filter { !it.isRemoved }.associate { it.uid to it.title }
    val byUid = categories.associateBy { it.uid }
    val byKey = settings.associateBy { it.key }
    val thisMonth = YearMonth.from(today)
    val detected = detectRecurring(rows, reminders, today, titles)
    val added = settings.filter { it.manual && detected.none { d -> d.key == it.key } }.mapNotNull { manualRecurring(it.key, it.title, rows, today) }
    val addedIn = added.mapNotNull { it.categoryUid }.toSet()
    val items = (detected.filter { !(it.byCategory && it.categoryUid in addedIn) } + added).sortedByDescending { it.amount }
    return items.mapNotNull { item ->
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

/** Two payments of one bill, as far as a guess goes: amount within [LOOK_ALIKE_PERCENT] of [a]'s, day of the month within [LOOK_ALIKE_DAYS]. */
fun looksAlike(a: TxFull, b: TxFull): Boolean =
    abs(a.amount - b.amount) * 100 <= a.amount * LOOK_ALIKE_PERCENT && abs(LocalDate.parse(a.date).dayOfMonth - LocalDate.parse(b.date).dayOfMonth) <= LOOK_ALIKE_DAYS

/**
 * A finished move: what to say, and the payments as they were (for Undo). A bill added by hand also carries its [billKey] and its settings
 * from before ([billBefore], null when it was new), so Undo takes the bill away again.
 */
class MoveDone(val message: String, val before: List<TransactionEntity>, val billKey: String? = null, val billBefore: BillEntity? = null)

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

    /**
     * Fixed monthly bill on, or back to Automatic, flipping what is saved now: a second tap before the card redrew must undo the first,
     * not repeat it (seen as a flaky test on CI).
     */
    suspend fun toggleFixed(bill: Bill) {
        fixedLock.withLock {
            val category = bill.category?.uid?.let { db.categoryDao().get(it) } ?: return
            val fixed = category.projectionMode == ProjectionMode.Fixed
            ledger.saveCategory(category.copy(projectionMode = if (fixed) ProjectionMode.Auto else ProjectionMode.Fixed))
        }
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

    /** Acts once on ends that have passed: the reminder stops, and a bill that is its whole category sets that category back to Automatic. Called by the reminder job. */
    suspend fun settleEnded(today: LocalDate) {
        val thisMonth = YearMonth.from(today)
        db.billDao().getAll().filter { it.endMonth != null && !it.settled && YearMonth.parse(it.endMonth) < thisMonth }.forEach { ended ->
            if (ended.key.startsWith("category:")) db.categoryDao().get(ended.key.removePrefix("category:"))
                ?.takeIf { it.projectionMode == ProjectionMode.Fixed }?.let { ledger.saveCategory(it.copy(projectionMode = ProjectionMode.Auto)) }
            db.billDao().upsert(ended.copy(reminderOn = false, settled = true))
        }
        // No changed() here: the reminder job calls this and books its own next run; re-booking from inside it replaced (cancelled) the
        // running job before it could post anything, over and over.
    }

    suspend fun moveCandidates(bill: Bill): MoveCandidates =
        moveCandidates(bill.item, bill.item.categoryUid?.let { db.transactionDao().getFullInCategory(it) }.orEmpty())

    private val fixedLock = kotlinx.coroutines.sync.Mutex() // taps take turns: each reads what the one before saved

    /** The last move, until undone or dismissed: the Recurring page offers Undo for it after the move screen closes. */
    val lastMove = kotlinx.coroutines.flow.MutableStateFlow<MoveDone?>(null)

    /** Moves the payments to [categoryUid] (named [categoryTitle]); with [addComment], uncommented ones get the bill's name. */
    suspend fun move(bill: Bill, uids: Collection<String>, categoryUid: String, categoryTitle: String, addComment: Boolean) {
        val before = ledger.moveToCategory(uids, categoryUid, bill.item.title.takeIf { addComment })
        lastMove.value = MoveDone("Moved ${before.size} payment${if (before.size == 1) "" else "s"} to $categoryTitle", before)
    }

    /**
     * Adds a bill by hand: the ticked payments [uids] get [title] as their comment when [setComment] (then the bill is found by that comment),
     * and move to [categoryUid] when given (otherwise the bill is that category's). Undo is offered like after a move.
     */
    suspend fun addManual(title: String, uids: Collection<String>, categoryUid: String?, categoryTitle: String?, setComment: Boolean, amount: Long) {
        val name = title.trim()
        val key = if (setComment || categoryUid == null) "comment:${name.lowercase()}" else "category:$categoryUid"
        val before = ledger.moveToCategory(uids, categoryUid, name.takeIf { setComment || categoryUid == null }, replaceComment = true)
        val saved = db.billDao().get(key)
        save((saved ?: BillEntity(key, name, categoryUid, amount)).copy(title = name, manual = true))
        lastMove.value = MoveDone("Added $name" + (categoryTitle?.let { " · moved ${before.size} to $it" } ?: ""), before, key, saved)
    }

    /**
     * Renames a bill (the pencil on Recurring). Its payments get [name] as their comment, so it is found by that name and its reminder says it,
     * and what was set for it (reminder, end) moves to the new key. A bill found by category becomes a hand-added bill of that category named
     * [name]; only its uncommented payments get the comment, so other notes in the category stay. Returns false for a blank name or one another bill has.
     */
    suspend fun rename(bill: Bill, name: String): Boolean {
        val clean = name.trim()
        val key = "comment:${clean.lowercase()}"
        if (clean.isEmpty()) return false
        if (key != bill.key && (db.billDao().get(key) != null || current(days.first()).any { it.key == key })) return false
        val byCategory = bill.key.startsWith("category:")
        val oldComment = bill.key.removePrefix("comment:")
        val payments = db.transactionDao().getAll().filter { t ->
            !t.isRemoved && t.type == TransactionType.Expense &&
                if (byCategory) t.categoryUid == bill.item.categoryUid else t.comment?.trim()?.lowercase() == oldComment
        }
        ledger.moveToCategory(payments.map { it.uid }, null, clean, replaceComment = !byCategory)
        val old = bill.settings
        if (old != null || byCategory) save(base(bill).copy(key = key, title = clean, manual = byCategory || old?.manual == true))
        if (key != bill.key && old != null) db.billDao().delete(bill.key)
        return true
    }

    suspend fun undo(done: MoveDone) {
        ledger.restore(done.before)
        done.billKey?.let { key -> done.billBefore?.let { db.billDao().upsert(it) } ?: db.billDao().delete(key); changed() }
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
