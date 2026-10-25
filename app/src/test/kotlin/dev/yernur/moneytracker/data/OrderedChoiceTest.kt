package dev.yernur.moneytracker.data

import org.junit.Assert.assertEquals
import org.junit.Test

class OrderedChoiceTest {
    private val pool = listOf("a", "b", "c", "d", "e", "f")
    private val default = listOf("a", "b", "c")

    @Test
    fun sanitize_dropsUnknownAndDuplicates_andFallsBackWhenTheSizeIsOutOfRange() {
        assertEquals(listOf("c", "a"), OrderedChoice.sanitize(listOf("c", "zzz", "a", "c"), pool, default, 2, 5))
        assertEquals(default, OrderedChoice.sanitize(listOf("a"), pool, default, 2, 5)) // too few
        assertEquals(default, OrderedChoice.sanitize(pool, pool, default, 2, 5)) // too many
        assertEquals(default, OrderedChoice.sanitize(null, pool, default, 2, 5))
    }

    @Test
    fun toggle_respectsTheMinimumAndMaximum() {
        assertEquals(listOf("a", "b"), OrderedChoice.toggle(listOf("a", "b", "c"), "c", 2, 3))
        assertEquals(listOf("a", "b"), OrderedChoice.toggle(listOf("a", "b"), "a", 2, 3)) // cannot go below 2
        assertEquals(listOf("a", "b", "d"), OrderedChoice.toggle(listOf("a", "b"), "d", 2, 3)) // appended at the end
        assertEquals(listOf("a", "b", "c"), OrderedChoice.toggle(listOf("a", "b", "c"), "d", 2, 3)) // cannot go above 3
    }

    @Test
    fun move_swapsWithTheNeighbour_andStopsAtTheEnds() {
        val list = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), OrderedChoice.move(list, "b", -1))
        assertEquals(listOf("a", "c", "b"), OrderedChoice.move(list, "b", 1))
        assertEquals(list, OrderedChoice.move(list, "a", -1))
        assertEquals(list, OrderedChoice.move(list, "c", 1))
        assertEquals(list, OrderedChoice.move(list, "zzz", 1))
    }

    @Test
    fun arranged_listsShownItemsInOrder_thenHiddenOnesInPoolOrder() {
        assertEquals(
            listOf("c" to true, "a" to true, "b" to false, "d" to false),
            OrderedChoice.arranged(listOf("c", "a"), listOf("a", "b", "c", "d")),
        )
    }
}
