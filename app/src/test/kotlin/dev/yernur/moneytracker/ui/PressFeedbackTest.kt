package dev.yernur.moneytracker.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PressFeedbackTest {
    @Test
    fun restingElements_areNotScaled() {
        assertEquals(1f, pressScale(300f, 3f, 0f), 0f)
        assertEquals(1f, pressScale(0f, 3f, 1f), 0f) // not measured yet
    }

    @Test
    fun aSmallButtonShrinksMore_thanAFullWidthRow_butNeverBeyondTheLimits() {
        val button = pressScale(150f, 3f, 1f) // a 50 dp button
        val row = pressScale(1000f, 3f, 1f)   // a row about 330 dp wide
        assertTrue(button < row)
        assertEquals(0.92f, button, 0.001f)   // capped at 8 %
        assertEquals(0.979f, row, 0.001f)     // 7 dp of 333 dp
        assertEquals(0.985f, pressScale(5000f, 3f, 1f), 0.0001f) // floor of 1.5 %
    }

    @Test
    fun halfwayThroughThePress_isHalfwayBetween() {
        val full = pressScale(150f, 3f, 1f)
        assertEquals((1f + full) / 2f, pressScale(150f, 3f, 0.5f), 0.0001f)
    }
}
