package dev.yernur.moneytracker.data

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorTest {
    private fun eval(expression: String) = Calculator.evaluate(expression)?.let(Calculator::format)

    /** Types the keys one by one, like the keypad does. */
    private fun type(vararg keys: String) = keys.fold("") { expression, key -> Calculator.press(expression, key) }

    @Test
    fun precedenceParenthesesAndSigns() {
        assertEquals("14", eval("2+3×4"))
        assertEquals("20", eval("(2+3)×4"))
        assertEquals("-5", eval("−5"))
        assertEquals("7", eval("10−−3−6"))
        assertEquals("2", eval("12÷2÷3"))
        assertEquals("5", eval("(2+3")) // unclosed parenthesis is closed
    }

    @Test
    fun decimalsAreExact() {
        assertEquals("0.3", eval("0.1+0.2"))
        assertEquals("0.19", eval("0,19")) // comma decimal
        assertEquals("1353.7", eval("1353.70"))
        assertEquals("0.333333333333", eval("1÷3"))
        assertEquals("1", eval("1÷3×3")) // extra internal precision, rounded only for display
    }

    @Test
    fun percentWorksLikeThePhoneCalculator() {
        assertEquals("0.5", eval("50%"))
        assertEquals("20", eval("200×10%"))
        assertEquals("1100", eval("1000+10%"))
        assertEquals("900", eval("1000−10%"))
        assertEquals("5.1", eval("10%+5"))
    }

    @Test
    fun invalidOrIncompleteExpressionsGiveNull() {
        assertNull(eval(""))
        assertNull(eval("5+"))
        assertNull(eval("1÷0"))
        assertNull(eval("1.2.3"))
        assertNull(eval("()"))
        assertNull(eval("2)"))
    }

    @Test
    fun keypad_buildsAndEvaluatesLikeAnAndroidCalculator() {
        assertEquals("12×3", type("1", "2", "×", "3"))
        assertEquals("36", type("1", "2", "×", "3", "="))
        assertEquals("5", type("0", "0", "5")) // no leading zeros
        assertEquals("0.5", type(".", "5")) // a lone "." becomes "0."
        assertEquals("1.5", type("1", ".", ".", "5")) // one decimal point per number
    }

    @Test
    fun keypad_operatorsReplaceEachOther_andDoNotStartAnExpression() {
        assertEquals("", type("×"))
        assertEquals("−", type("−"))
        assertEquals("−", type("−", "+")) // a lone sign cannot be changed into a leading plus
        assertEquals("5÷", type("5", "+", "−", "×", "÷"))
        assertEquals("(−", type("(", "×", "−")) // "(" may only be followed by a minus sign
    }

    @Test
    fun keypad_smartParenthesesAndImplicitMultiplication() {
        assertEquals("(2+3)", type("()", "2", "+", "3", "()"))
        assertEquals("2×(", type("2", "()"))
        assertEquals("(2+3)×4", type("()", "2", "+", "3", "()", "4"))
        assertEquals("(2+3)×(", type("()", "2", "+", "3", "()", "()")) // after a closed group, "(" multiplies
    }

    @Test
    fun keypad_deleteAndClear() {
        assertEquals("1", type("1", "2", "DEL"))
        assertEquals("", type("1", "2", "C"))
        assertEquals("5+", type("5", "+", "=")) // "=" on an incomplete expression changes nothing
        assertEquals("0.3", type("0", ".", "1", "+", "0", ".", "2", "="))
    }

    @Test
    fun resultFeedsTheAmountParser() {
        val value = Calculator.evaluate("1500+250,50")!!
        assertEquals(175_050L, Money.parseMinor(value.toPlainString()))
        assertEquals(BigDecimal("1750.50"), value)
    }
}
