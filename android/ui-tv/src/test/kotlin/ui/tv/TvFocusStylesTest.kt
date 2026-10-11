package ui.tv

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import designsystem.Accent
import designsystem.TvStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What a focused card or pill draws in each style; mediagram's are today's. */
class TvFocusStylesTest {
    private val accent = Accent.Default.dark

    @Test
    fun mediagramCardsTakeTheAccentBorderAndNoGlow() {
        val border = focusedCardBorderOf(TvStyle.MEDIAGRAM.look, accent, RectangleShape)
        assertEquals(accent, border.border.brush.let { (it as androidx.compose.ui.graphics.SolidColor).value })
        assertEquals(TvFocus.BorderWidth, border.border.width)
        assertNull(focusedCardGlowOf(TvStyle.MEDIAGRAM.look))
    }

    @Test
    fun appleCardsLiftWithNoBorderAndNoShadow() {
        assertEquals(Border.None, focusedCardBorderOf(TvStyle.APPLE.look, accent, RoundedCornerShape(12.dp)))
        assertNull(focusedCardGlowOf(TvStyle.APPLE.look))
    }

    @Test
    fun googleCardsTakeAWhiteRingAndNoGlow() {
        val shape = RoundedCornerShape(16.dp)
        val border = focusedCardBorderOf(TvStyle.GOOGLE.look, accent, shape)
        assertEquals(Color.White, (border.border.brush as androidx.compose.ui.graphics.SolidColor).value)
        assertEquals(shape, border.shape)
        assertNull(focusedCardGlowOf(TvStyle.GOOGLE.look))
    }

    @Test
    fun surfacesRingInTheAccentForMediagramAndWhiteOtherwise() {
        assertEquals(accent, focusInkOf(TvStyle.MEDIAGRAM.look, accent))
        assertEquals(Color.White, focusInkOf(TvStyle.APPLE.look, accent))
        assertEquals(Color.White, focusInkOf(TvStyle.GOOGLE.look, accent))
    }

    /** The current swatch never wears the focused one's ring: the ink beside mediagram's accent focus, the accent beside the white. */
    @Test
    fun aChosenSurfaceRingsInAColourFocusDoesNotUse() {
        val ink = Color(0xFFF6F2EA)
        assertEquals(ink, selectedInkOf(TvStyle.MEDIAGRAM.look, accent, ink))
        assertEquals(accent, selectedInkOf(TvStyle.APPLE.look, accent, ink))
        assertEquals(accent, selectedInkOf(TvStyle.GOOGLE.look, accent, ink))
    }

    @Test
    fun aFocusedPillFillsWhiteWithDarkInkOnlyWhereTheStyleSaysSo() {
        val rest = Color(0x1FFFFFFF) to Color(0xFFF6F2EA)
        assertEquals(rest, pillFocusedColoursOf(TvStyle.MEDIAGRAM.look, rest.first, rest.second))
        assertEquals(FocusFill to FocusFillInk, pillFocusedColoursOf(TvStyle.APPLE.look, rest.first, rest.second))
        assertEquals(FocusFill to FocusFillInk, pillFocusedColoursOf(TvStyle.GOOGLE.look, rest.first, rest.second))
    }
}
