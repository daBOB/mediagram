package ui.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingLineTest {
    private fun card(path: String?, left: Float, top: Float, width: Float = 100f, height: Float = 150f) =
        PlacedCard(path ?: Any(), path, left, left + width, top, top + height)

    @Test
    fun picksTheLeftMostCardOfTheRowNearestTheLine() {
        val upper = listOf(card("a1", 0f, 0f), card("a2", 120f, 0f))
        val lower = listOf(card("b1", 40f, 400f), card("b2", 160f, 400f))
        assertEquals("b1", cardNearestLine(upper + lower, line = 380f)?.posterPath)
        assertEquals("a1", cardNearestLine((upper + lower).reversed(), line = 100f)?.posterPath)
    }

    @Test
    fun skipsCardsScrolledOffTheLeftAndCardsWithoutAPicture() {
        val cards = listOf(card("gone", -150f, 0f), card(null, 0f, 0f), card("shown", 60f, 0f))
        assertEquals("shown", cardNearestLine(cards, line = 75f)?.posterPath)
    }

    @Test
    fun nothingToPickWithNoCards() {
        assertNull(cardNearestLine(emptyList(), line = 300f))
    }
}
