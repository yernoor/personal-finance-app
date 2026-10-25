package dev.yernur.moneytracker.data

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The expression engine behind the amount calculator. Pure BigDecimal, so 0.1 + 0.2 is exactly 0.3.
 * Percent works like the phone's calculator: `50%` is 0.5, `200 × 10%` is 20, `1000 + 10%` is 1100.
 */
object Calculator {
    /** Divide with extra digits and round only for display, so 1 ÷ 3 × 3 shows 1. */
    private const val DIVISION_SCALE = 24
    private const val DISPLAY_SCALE = 12
    private const val OPERATORS = "+−×÷"

    /** Null when the expression is incomplete or invalid (e.g. "5+", "1÷0"). Unclosed parentheses are closed for you. */
    fun evaluate(expression: String): BigDecimal? {
        val text = normalize(expression)
        if (text.isEmpty()) return null
        return try {
            Parser(text).parseAll()
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** "12.5", never "1.2E+1": for the display and for feeding the amount field. */
    fun format(value: BigDecimal): String = value.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    /** One key press: [key] is a digit, ".", one of + − × ÷, "()" (smart parenthesis), "%", "DEL", "C" or "=". */
    fun press(expression: String, key: String): String = when (key) {
        "C" -> ""
        "DEL" -> expression.dropLast(1)
        "=" -> evaluate(expression)?.let(::format) ?: expression
        "." -> pressDecimal(expression)
        "()" -> pressParenthesis(expression)
        "%" -> if (expression.lastOrNull()?.let { it.isDigit() || it == ')' || it == '%' } == true) "$expression%" else expression
        in listOf("+", "−", "×", "÷") -> pressOperator(expression, key.single())
        else -> pressDigit(expression, key) // "0".."9"
    }

    private fun trailingNumber(expression: String) = expression.takeLastWhile { it.isDigit() || it == '.' }

    private fun pressDigit(expression: String, digit: String): String = when {
        expression.lastOrNull().let { it == ')' || it == '%' } -> "$expression×$digit"
        trailingNumber(expression) == "0" -> expression.dropLast(1) + digit
        else -> expression + digit
    }

    private fun pressDecimal(expression: String): String {
        val number = trailingNumber(expression)
        return when {
            '.' in number -> expression
            number.isEmpty() -> "${expression}0."
            else -> "$expression."
        }
    }

    private fun pressOperator(expression: String, operator: Char): String {
        val last = expression.lastOrNull()
        return when {
            last == null || last == '(' -> if (operator == '−') expression + operator else expression
            last in OPERATORS -> if (expression.length == 1) expression else expression.dropLast(1) + operator
            else -> expression + operator
        }
    }

    private fun pressParenthesis(expression: String): String {
        val last = expression.lastOrNull()
        val afterValue = last != null && (last.isDigit() || last == ')' || last == '%')
        val open = expression.count { it == '(' }
        val close = expression.count { it == ')' }
        return when {
            open > close && afterValue -> "$expression)"
            afterValue -> "$expression×("
            else -> "$expression("
        }
    }

    private fun normalize(expression: String): String {
        val text = expression.replace(" ", "").replace(',', '.').replace('*', '×').replace('/', '÷').replace('–', '−').replace('-', '−')
        return text + ")".repeat((text.count { it == '(' } - text.count { it == ')' }).coerceAtLeast(0))
    }

    /** expr = term (("+"|"−") term)* ; term = factor (("×"|"÷") factor)* ; factor = sign* primary "%"* */
    private class Parser(private val s: String) {
        private var i = 0

        /** [percent]: the value came from `x%`, so "a + x%" means "a + a·x/100". */
        private class Value(val n: BigDecimal, val percent: Boolean)

        fun parseAll(): BigDecimal {
            val result = expr()
            require(i == s.length) { "unexpected '${s[i]}'" }
            return result.n
        }

        private fun peek(): Char? = s.getOrNull(i)

        private fun expr(): Value {
            var left = term()
            while (peek() == '+' || peek() == '−') {
                val op = s[i++]
                val right = term()
                val delta = if (right.percent) left.n.multiply(right.n) else right.n
                left = Value(if (op == '+') left.n.add(delta) else left.n.subtract(delta), false)
            }
            return left
        }

        private fun term(): Value {
            var left = factor()
            while (peek() == '×' || peek() == '÷') {
                val op = s[i++]
                val right = factor()
                left = if (op == '×') {
                    Value(left.n.multiply(right.n), false)
                } else {
                    require(right.n.signum() != 0) { "division by zero" }
                    Value(left.n.divide(right.n, DIVISION_SCALE, RoundingMode.HALF_UP), false)
                }
            }
            return left
        }

        private fun factor(): Value {
            when (peek()) {
                '−' -> { i++; return factor().let { Value(it.n.negate(), it.percent) } }
                '+' -> { i++; return factor() }
            }
            var value = primary()
            while (peek() == '%') {
                i++
                value = Value(value.n.movePointLeft(2), true)
            }
            return value
        }

        private fun primary(): Value {
            if (peek() == '(') {
                i++
                val inner = expr()
                require(peek() == ')') { "missing ')'" }
                i++
                return Value(inner.n, false)
            }
            val start = i
            while (peek()?.let { it.isDigit() || it == '.' } == true) i++
            require(i > start) { "number expected" }
            return Value(BigDecimal(s.substring(start, i)), false) // NumberFormatException is an IllegalArgumentException
        }
    }
}
