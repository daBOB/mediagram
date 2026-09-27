package ui.chrome

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The departments bar's own see-through-to-solid fraction, read from the
 * Home grid's actual position — not the scroll deltas a nested-scroll
 * connection would otherwise have to track and lose on every recomposition.
 */
class CoverBlendTest {
    private val viewport = 1000f

    @Test
    fun atTheVeryTopTheBarIsFullyTranslucent() {
        assertEquals(0f, coverBlend(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0, viewportPx = viewport))
    }

    @Test
    fun beforeFortyPercentScrolledStaysFullyTranslucent() {
        assertEquals(0f, coverBlend(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 300, viewportPx = viewport))
    }

    @Test
    fun betweenFortyAndSeventyFivePercentBlendsLinearly() {
        val blend = coverBlend(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 575, viewportPx = viewport)
        assertEquals(0.5f, blend, absoluteTolerance = 0.001f)
    }

    @Test
    fun pastSeventyFivePercentScrolledIsFullySolid() {
        assertEquals(1f, coverBlend(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 800, viewportPx = viewport))
    }

    @Test
    fun onceTheSecondItemIsFirstVisibleTheCoverIsGoneSoTheBarIsFullySolid() {
        // A deep scroll (or a position restored after a back-navigate)
        // leaves item 0 behind entirely — the offset into item 1 says
        // nothing about how far past the cover this is, so it is not read.
        assertEquals(1f, coverBlend(firstVisibleItemIndex = 1, firstVisibleItemScrollOffset = 5, viewportPx = viewport))
    }

    private fun assertEquals(
        expected: Float,
        actual: Float,
        absoluteTolerance: Float,
    ) {
        kotlin.test.assertTrue(kotlin.math.abs(expected - actual) <= absoluteTolerance, "expected $expected, was $actual")
    }
}
