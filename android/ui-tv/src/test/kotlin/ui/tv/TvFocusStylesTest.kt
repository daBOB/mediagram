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
    fun appleCardsLiftWithAShadowAndNoBorder() {
        assertEquals(Border.None, focusedCardBorderOf(TvStyle.APPLE.look, accent, RoundedCornerShape(12.dp)))
        val glow = checkNotNull(focusedCardGlowOf(TvStyle.APPLE.look))
        assertEquals(16.dp, glow.elevation)
        assertEquals(Color.Black.copy(alpha = 0.6f), glow.elevationColor)
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

    @Test
    fun aFocusedPillFillsWhiteWithDarkInkOnlyWhereTheStyleSaysSo() {
        val rest = Color(0x1FFFFFFF) to Color(0xFFF6F2EA)
        assertEquals(rest, pillFocusedColoursOf(TvStyle.MEDIAGRAM.look, rest.first, rest.second))
        assertEquals(FocusFill to FocusFillInk, pillFocusedColoursOf(TvStyle.APPLE.look, rest.first, rest.second))
        assertEquals(FocusFill to FocusFillInk, pillFocusedColoursOf(TvStyle.GOOGLE.look, rest.first, rest.second))
    }
}
