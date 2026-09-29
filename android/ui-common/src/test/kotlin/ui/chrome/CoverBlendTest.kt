package ui.chrome

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The departments bar's own see-through-to-solid fraction, read from the
 * Home list's actual position — not the scroll deltas a nested-scroll
 * connection would otherwise have to track and lose on every recomposition.
 */
class CoverBlendTest {
    private val coverHeight = 2000f
    private val barHeight = 200f

    @Test
    fun atTheVeryTopTheBarIsFullyTranslucent() {
        assertEquals(0f, coverBlend(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0, coverHeightPx = coverHeight, barHeightPx = barHeight))
    }

    @Test
    fun beforeTheCoversBottomReachesTheBarsBottomStaysTranslucent() {
        val blend = coverBlend(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 900, coverHeightPx = coverHeight, barHeightPx = barHeight)
        assertEquals(0.5f, blend, absoluteTolerance = 0.001f)
    }

    @Test
    fun exactlyAtTheCoversBottomPassingTheBarsBottomIsFullySolid() {
        // threshold = coverHeight - barHeight = 1800
        assertEquals(1f, coverBlend(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 1800, coverHeightPx = coverHeight, barHeightPx = barHeight))
    }

    @Test
    fun pastTheCoversBottomStaysFullySolid() {
        assertEquals(1f, coverBlend(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 1_999_999, coverHeightPx = coverHeight, barHeightPx = barHeight))
    }

    @Test
    fun onceTheSecondItemIsFirstVisibleTheCoverIsGoneSoTheBarIsFullySolid() {
        // A deep scroll (or a position restored after a back-navigate)
        // leaves item 0 behind entirely — the offset into item 1 says
        // nothing about how far past the cover this is, so it is not read.
        assertEquals(1f, coverBlend(firstVisibleItemIndex = 1, firstVisibleItemScrollOffset = 5, coverHeightPx = coverHeight, barHeightPx = barHeight))
    }

    @Test
    fun aDeepScrollManyItemsPastTheCoverStaysFullySolid() {
        // "Latest series"/"Latest courses" territory — several sections, and
        // several items, past the cover.
        assertEquals(1f, coverBlend(firstVisibleItemIndex = 5, firstVisibleItemScrollOffset = 40, coverHeightPx = coverHeight, barHeightPx = barHeight))
    }

    @Test
    fun restoredDirectlyAtADeepPositionReadsSolidWithNoTransition() {
        // `coverBlend` is a pure function of the current position, not a
        // tracked delta with state of its own to catch up on — a position
        // restored after a back-navigate (or a rotation) reads solid on the
        // very first read, the same as one reached by scrolling there.
        assertEquals(1f, coverBlend(firstVisibleItemIndex = 6, firstVisibleItemScrollOffset = 0, coverHeightPx = coverHeight, barHeightPx = barHeight))
    }

    @Test
    fun aCoverNoTallerThanTheBarItselfIsAlwaysSolid() {
        // Degenerate but not impossible (a tiny window): the threshold floors
        // at 1px rather than going zero or negative and dividing oddly.
        assertEquals(1f, coverBlend(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 1, coverHeightPx = 150f, barHeightPx = 200f))
    }

    private fun assertEquals(
        expected: Float,
        actual: Float,
        absoluteTolerance: Float,
    ) {
        kotlin.test.assertTrue(kotlin.math.abs(expected - actual) <= absoluteTolerance, "expected $expected, was $actual")
    }
}
