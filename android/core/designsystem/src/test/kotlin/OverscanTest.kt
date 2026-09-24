package designsystem

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 48dp x 27dp is 5% of the 960x540dp TV viewport on each axis — the
 * broadcast-standard "TV-safe" overscan margin, carried into dp so the same
 * numbers hold no matter which set ends up cropping or scaling the frame.
 * Pinned here so a future edit can't drift it off that 5% without noticing.
 */
class OverscanTest {
    @Test
    fun matchesFivePercentOfTheTvViewport() {
        assertEquals(48.dp, Overscan.horizontal)
        assertEquals(27.dp, Overscan.vertical)
    }
}
