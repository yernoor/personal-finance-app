package dev.yernur.moneytracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.yernur.moneytracker.data.PeriodUnit

/** How the shown period just changed. Charts slides its bars one slot for [Earlier] / [Later]; anything else comes in with the screen's slide. */
enum class PeriodCue { None, ZoomOut, ZoomIn, Earlier, Later }

/** A bigger unit is a zoom out, a smaller one a zoom in; the same unit is a step the way you are going. [pos] grows towards the present. */
fun periodCue(from: PeriodUnit, fromPos: Long, to: PeriodUnit, toPos: Long): PeriodCue = when {
    to.ordinal > from.ordinal -> PeriodCue.ZoomOut
    to.ordinal < from.ordinal -> PeriodCue.ZoomIn
    toPos < fromPos -> PeriodCue.Earlier
    toPos > fromPos -> PeriodCue.Later
    else -> PeriodCue.None
}

const val PERIOD_MS = 450

/** Remembers the period it saw last, so a change of unit or position can be told apart from a change of data. */
class CueTracker(private var unit: PeriodUnit, private var pos: Long) {
    private var pending = PeriodCue.None

    fun update(newUnit: PeriodUnit, newPos: Long) {
        if (newUnit == unit && newPos == pos) return
        pending = periodCue(unit, pos, newUnit, newPos)
        unit = newUnit
        pos = newPos
    }

    /** The cue of the period change since the last call, once. */
    fun consume(): PeriodCue = pending.also { pending = PeriodCue.None }
}

@Composable
fun rememberCueTracker(unit: PeriodUnit, pos: Long): CueTracker {
    val tracker = remember { CueTracker(unit, pos) }
    tracker.update(unit, pos)
    return tracker
}
