package catalog

import model.Kind
import model.MediaSet
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
    /** Minutes that round up to a whole hour carry into it; "2h 60m" is not a runtime. */
    @Test
    fun aRoundedUpHourCarriesInsteadOfPrintingSixtyMinutes() {
        assertEquals("3h", humanDuration(10_780))
        assertEquals("1h", humanDuration(3_590))
    }

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

    /** A film's page names what kind of film it is, as `film-page.js` does — three genres at most. */
    @Test
    fun aFilmsFirstThreeGenresCloseTheLine() {
        assertEquals(
            "2004 · 1h 53m · FSK 12 · Drama, Crime, Thriller",
            factsLine(2004, 6_780, "FSK 12", listOf("Drama", "Crime", "Thriller", "Mystery")),
        )
        assertEquals("Drama", factsLine(null, null, genres = listOf("Drama")))
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

    @Test
    fun aCoverStoryPutsTheScoreAfterTheFactsAndSaysNothingWhenBothAreMissing() {
        val film =
            MediaSet(
                setId = "f", kind = Kind.MOVIE, title = "f", show = null, chapter = null, path = null, season = null,
                episodeFirst = null, episodeLast = null, year = 2004, durationSecs = 6780, posterPath = null, totalBytes = 0,
                fsk = "12", rating = 5.9,
            )
        assertEquals("2004 · 1h 53m · FSK 12 · ★ 5.9", coverFactsLine(film))
        assertEquals("★ 5.9", coverFactsLine(film.copy(year = null, durationSecs = null, fsk = null)))
        assertNull(coverFactsLine(film.copy(year = null, durationSecs = null, fsk = null, rating = null)))
    }
}
