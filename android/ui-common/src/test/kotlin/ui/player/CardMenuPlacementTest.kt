package ui.player

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Test
import kotlin.test.assertEquals

/** Where a card's menu opens: just above the card, centred on the button that opened it, inside the card's width. */
class CardMenuPlacementTest {
    private val card = IntRect(left = 100, top = 800, right = 900, bottom = 1_000)

    @Test
    fun aMenuSitsAboveTheCardCentredOnItsButton() {
        val anchor = IntRect(left = 400, top = 900, right = 460, bottom = 948)

        assertEquals(IntOffset(330, 592), cardMenuOffset(anchor, card, IntSize(200, 200), gap = 8))
    }

    @Test
    fun aMenuOverTheCardsLeftEdgeStaysInsideIt() {
        val anchor = IntRect(left = 110, top = 900, right = 158, bottom = 948)

        assertEquals(100, cardMenuOffset(anchor, card, IntSize(200, 100), gap = 8).x)
    }

    @Test
    fun aMenuOverTheCardsRightEdgeStaysInsideIt() {
        val anchor = IntRect(left = 850, top = 900, right = 898, bottom = 948)

        assertEquals(700, cardMenuOffset(anchor, card, IntSize(200, 100), gap = 8).x)
    }

    @Test
    fun aMenuWiderThanTheCardStartsAtItsLeftEdge() {
        val anchor = IntRect(left = 400, top = 900, right = 460, bottom = 948)

        assertEquals(100, cardMenuOffset(anchor, card, IntSize(1_000, 100), gap = 8).x)
    }

    @Test
    fun aMenuTallerThanTheRoomAboveStopsAtTheTop() {
        val anchor = IntRect(left = 400, top = 900, right = 460, bottom = 948)

        assertEquals(0, cardMenuOffset(anchor, card, IntSize(200, 900), gap = 8).y)
    }
}
