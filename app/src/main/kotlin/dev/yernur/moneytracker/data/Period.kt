package dev.yernur.moneytracker.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class PeriodUnit { Day, Week, Month, Year }

/** Inclusive on both ends. */
data class DateRange(val from: LocalDate, val to: LocalDate) {
    operator fun contains(date: LocalDate) = date >= from && date <= to
}

/** The owner's backup has `firstWeekday = monday`. */
val DEFAULT_FIRST_DAY = DayOfWeek.MONDAY

fun PeriodUnit.rangeOf(date: LocalDate, firstDay: DayOfWeek = DEFAULT_FIRST_DAY): DateRange = when (this) {
    PeriodUnit.Day -> DateRange(date, date)
    PeriodUnit.Week -> date.with(TemporalAdjusters.previousOrSame(firstDay)).let { DateRange(it, it.plusDays(6)) }
    PeriodUnit.Month -> DateRange(date.withDayOfMonth(1), date.with(TemporalAdjusters.lastDayOfMonth()))
    PeriodUnit.Year -> DateRange(date.withDayOfYear(1), date.with(TemporalAdjusters.lastDayOfYear()))
}

fun PeriodUnit.shift(date: LocalDate, by: Long): LocalDate = when (this) {
    PeriodUnit.Day -> date.plusDays(by)
    PeriodUnit.Week -> date.plusWeeks(by)
    PeriodUnit.Month -> date.plusMonths(by)
    PeriodUnit.Year -> date.plusYears(by)
}

/** [count] consecutive ranges ending with the one that contains [date], oldest first. */
fun PeriodUnit.lastRanges(date: LocalDate, count: Int, firstDay: DayOfWeek = DEFAULT_FIRST_DAY): List<DateRange> =
    (count - 1 downTo 0).map { rangeOf(shift(date, -it.toLong()), firstDay) }
