package ui.chrome

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The bar's own background at the two ends of [blend] — plain JUnit, the
 * same reason [CoverBlendTest] needs no Robolectric. With a backdrop blur
 * behind it the scrolled bar stays translucent, the web's 72% paper; without
 * one it has to be opaque on its own, so a deep scroll (or a position
 * restored deep) reads fully opaque, not just close.
 */
class DepartmentsBarColorTest {
    private val paper = Color(0xFF0D0D0E)

    @Test
    fun aDeepScrollWithoutABlurReadsFullyOpaque() {
        assertEquals(1f, barBackground(paper, blend = 1f, frosted = false).alpha)
        assertEquals(paper, barBackground(paper, blend = 1f, frosted = false))
    }

    @Test
    fun aDeepScrollOverABlurKeepsTheWebsPaperTint() {
        assertEquals(paper.copy(alpha = 0.72f), barBackground(paper, blend = 1f, frosted = true))
    }

    @Test
    fun overTheCoverStaysTranslucentEitherWay() {
        assertEquals(OverCoverBg, barBackground(paper, blend = 0f, frosted = false))
        assertEquals(OverCoverBg, barBackground(paper, blend = 0f, frosted = true))
    }

    @Test
    fun theBlurNeedsApi31() {
        assertFalse(frostAvailable(sdk = 30))
        assertTrue(frostAvailable(sdk = 31))
    }
}
