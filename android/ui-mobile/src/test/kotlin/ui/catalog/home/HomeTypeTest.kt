package ui.catalog.home

import kotlin.test.Test
import kotlin.test.assertEquals

/** [fluid] is arithmetic, not layout — plain JUnit, the same reason `CoverBlendTest` needs no Robolectric. */
class HomeTypeTest {
    @Test
    fun belowTheMinimumClampsToTheMinimum() {
        assertEquals(48f, fluid(min = 48f, fraction = 0.064f, max = 112f, width = 200f))
    }

    @Test
    fun aboveTheMaximumClampsToTheMaximum() {
        assertEquals(112f, fluid(min = 48f, fraction = 0.064f, max = 112f, width = 4000f))
    }

    @Test
    fun betweenTheTwoFollowsTheFraction() {
        // The web's own cover-title clamp, at the tablet's own window width.
        assertEquals(74.496f, fluid(min = 48f, fraction = 0.064f, max = 112f, width = 1164f), absoluteTolerance = 0.001f)
    }

    @Test
    fun gutterFollowsTheSameClampInDp() {
        assertEquals(37.248f, gutterFor(1164.0f.dp()).value, absoluteTolerance = 0.001f)
    }

    @Test
    fun countOfSpellsSmallCountsAsWords() {
        // A verified web fixture (`home-web-3.png`): "Boardwalk Empire" reads
        // "fourteen episodes · two seasons" on the actual reference shot.
        assertEquals("one episode", countOf(1, "episode"))
        assertEquals("fourteen episodes", countOf(14, "episode"))
        assertEquals("two seasons", countOf(2, "season"))
    }

    @Test
    fun countOfFallsBackToFiguresPastTwenty() {
        // Same reference shot: "Schitt's Creek" reads "21 episodes · three seasons".
        assertEquals("21 episodes", countOf(21, "episode"))
        assertEquals("three seasons", countOf(3, "season"))
    }

    private fun Float.dp() = androidx.compose.ui.unit.Dp(this)

    private fun assertEquals(
        expected: Float,
        actual: Float,
        absoluteTolerance: Float,
    ) {
        kotlin.test.assertTrue(kotlin.math.abs(expected - actual) <= absoluteTolerance, "expected $expected, was $actual")
    }
}
