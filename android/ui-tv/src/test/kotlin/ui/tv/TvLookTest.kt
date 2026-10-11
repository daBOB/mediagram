package ui.tv

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import designsystem.Palette
import designsystem.TvStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Each style's look carries the spec's values; mediagram's are today's, so nothing it draws moves. */
class TvLookTest {
    @Test
    fun mediagramKeepsTodaysSquareAccentFocusAndTheRail() {
        val look = TvStyle.MEDIAGRAM.look
        assertEquals(RectangleShape, look.cardShape)
        assertEquals(TvCardFocus.ACCENT_BORDER, look.cardFocus)
        assertEquals(TvFocus.Scale, look.focusScale)
        assertFalse(look.pillFocusFills)
        assertFalse(look.landscapeRows)
        assertEquals(Palette.Ground, look.neutrals.ground)
        assertTrue(TvStyle.MEDIAGRAM.hasRail)
    }

    @Test
    fun appleLiftsRoundedLandscapeCardsOnBlack() {
        val look = TvStyle.APPLE.look
        assertEquals(RoundedCornerShape(12.dp), look.cardShape)
        assertEquals(TvCardFocus.LIFT, look.cardFocus)
        assertEquals(1.10f, look.focusScale)
        assertTrue(look.pillFocusFills)
        assertTrue(look.landscapeRows)
        assertEquals(Color(0xFF000000), look.neutrals.ground)
        assertFalse(TvStyle.APPLE.hasRail)
    }

    @Test
    fun googleRingsRoundedLandscapeCardsOnDarkGrey() {
        val look = TvStyle.GOOGLE.look
        assertEquals(RoundedCornerShape(16.dp), look.cardShape)
        assertEquals(TvCardFocus.RING, look.cardFocus)
        assertEquals(1.05f, look.focusScale)
        assertTrue(look.pillFocusFills)
        assertTrue(look.landscapeRows)
        assertEquals(Color(0xFF202124), look.neutrals.ground)
        assertFalse(TvStyle.GOOGLE.hasRail)
    }
}
