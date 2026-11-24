package dev.yernur.moneytracker.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SetupStepsTest {
    @Test
    fun nothingDoneOnAnEmptyInstall() = assertEquals(emptySet<SetupStep>(), doneSetupSteps(0, 0, false, false, false))

    @Test
    fun eachStepTicksFromItsOwnSignal() {
        assertEquals(setOf(SetupStep.Accounts), doneSetupSteps(1, 0, false, false, false))
        assertEquals(setOf(SetupStep.Categories), doneSetupSteps(0, 3, false, false, false))
        assertEquals(setOf(SetupStep.FirstOperation), doneSetupSteps(0, 0, true, false, false))
        assertEquals(setOf(SetupStep.Capture), doneSetupSteps(0, 0, false, true, false))
        assertEquals(setOf(SetupStep.Analysis), doneSetupSteps(0, 0, false, false, true))
        assertEquals(SetupStep.entries.toSet(), doneSetupSteps(2, 5, true, true, true))
    }
}
