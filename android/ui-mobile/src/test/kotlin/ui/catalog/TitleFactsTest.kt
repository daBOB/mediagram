package ui.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The facts beside a poster. Pinned against the web player's `format.js`
 * and `series-summary.js`, because a viewer who uses both surfaces should
 * read one library described one way — and pinned on the absent cases too,
 * since most of what the index does not know it does not know quietly.
 */
class TitleFactsTest {
    @Test
    fun aFilmRunsForHoursAndMinutes() {
        assertEquals("1h 53m", humanDuration(6_780))
        assertEquals("2h 2m", humanDuration(7_320))
    }

    @Test
    fun underAnHourThereIsNoHourToPrint() {
        assertEquals("12m", humanDuration(720))
    }

    /** A whole number of hours reads as one; "2h 0m" is a zero nobody asked for. */
    @Test
    fun anExactHourDropsTheMinutes() {
        assertEquals("2h", humanDuration(7_200))
    }

    /**
     * A clip that rounds away to nothing is still a minute to a viewer
     * deciding whether to start it, and "0m" would read as broken.
     */
    @Test
    fun somethingShorterThanAMinuteIsStillAMinute() {
        assertEquals("1m", humanDuration(20))
    }

    @Test
    fun aRuntimeNobodyRecordedPrintsNothing() {
        assertNull(humanDuration(null))
        assertNull(humanDuration(0))
        assertNull(humanDuration(-30))
    }

    @Test
    fun theYearAndTheRuntimeShareOneLine() {
        assertEquals("2004 · 1h 53m", factsLine(2004, 6_780))
    }

    /** Half a line is worth printing; the separator alone is not. */
    @Test
    fun eitherHalfStandsOnItsOwn() {
        assertEquals("2004", factsLine(2004, null))
        assertEquals("1h 53m", factsLine(null, 6_780))
    }

    @Test
    fun nothingKnownPrintsNoLineAtAll() {
        assertNull(factsLine(null, null))
        // Zero is what an index writes for a year it does not have, not a
        // title from the year zero.
        assertNull(factsLine(0, null))
    }

    @Test
    fun aRatingCarriesItsStarAndOneDecimal() {
        assertEquals("★ 5.9", ratingLabel(5.9))
        assertEquals("★ 8.0", ratingLabel(8.0))
    }

    @Test
    fun aTitleNoProviderScoredShowsNoStar() {
        assertNull(ratingLabel(null))
    }
}
