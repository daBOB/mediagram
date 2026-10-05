package ui.player

import androidx.compose.ui.unit.IntRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the card has open, and the order Back and a tap close it in. */
class PlayerCardStateTest {

    @Test
    fun openingAMenuWhileAnotherIsOpenReplacesIt() {
        val card = PlayerCardState()
        card.toggle(CardMenu.Speed)
        card.toggle(CardMenu.Audio)

        assertEquals(CardMenu.Audio, card.menu)
    }

    @Test
    fun pressingTheOpenMenusButtonAgainClosesIt() {
        val card = PlayerCardState()
        card.toggle(CardMenu.Speed)
        card.toggle(CardMenu.Speed)

        assertNull(card.menu)
        assertFalse(card.somethingOpen)
    }

    @Test
    fun backClosesTheMenuBeforeTheSidebar() {
        val card = PlayerCardState()
        card.toggleSidebar()
        card.toggle(CardMenu.Framing)
        assertTrue(card.somethingOpen)

        card.closeTopmost()
        assertNull(card.menu)
        assertTrue(card.sidebarOpen)

        card.closeTopmost()
        assertFalse(card.sidebarOpen)
        assertFalse(card.somethingOpen)
    }

    @Test
    fun theEpisodesButtonClosesAnOpenMenuAndTogglesTheSidebar() {
        val card = PlayerCardState()
        card.toggle(CardMenu.Speed)

        card.toggleSidebar()
        assertNull(card.menu)
        assertTrue(card.sidebarOpen)

        card.toggleSidebar()
        assertFalse(card.sidebarOpen)
    }

    @Test
    fun aTapOnThePictureClosesAnOpenMenuRatherThanTheCard() {
        val card = PlayerCardState()
        assertFalse(card.dismissMenu(), "nothing open: the tap is the card's")

        card.toggle(CardMenu.Audio)
        assertTrue(card.dismissMenu())
        assertNull(card.menu)
    }

    @Test
    fun theStylePanelOpensInPlaceAboveTheSameButton() {
        val card = PlayerCardState()
        val dropDown = IntRect(left = 100, top = 900, right = 148, bottom = 948)
        card.anchor(CardMenu.Subtitles, dropDown)
        card.toggle(CardMenu.Subtitles)

        card.switchTo(CardMenu.SubtitleStyle)

        assertEquals(CardMenu.SubtitleStyle, card.menu)
        assertEquals(dropDown, card.anchorOf(CardMenu.SubtitleStyle))
    }
}
