package ui.chrome

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The bar's own background at the two ends of [blend] — plain JUnit, the
 * same reason [CoverBlendTest] needs no Robolectric. Android has no
 * backdrop blur behind the bar, so its solid state has to be opaque on its
 * own; a deep scroll (or a position restored deep) has to read fully
 * opaque, not just close.
 */
class DepartmentsBarColorTest {
    private val paper = Color(0xFF0D0D0E)

    @Test
    fun aDeepScrollReadsFullyOpaque() {
        assertEquals(1f, barBackground(paper, blend = 1f).alpha)
        assertEquals(paper, barBackground(paper, blend = 1f))
    }

    @Test
    fun overTheCoverStaysTranslucent() {
        assertEquals(OverCoverBg, barBackground(paper, blend = 0f))
    }
}
