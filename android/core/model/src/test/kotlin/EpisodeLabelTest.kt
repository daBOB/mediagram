package model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every episode shape the library index holds, pinned to what the web
 * player's `episodeLabel` prints for the same row (`web/test/format.test.ts`).
 * An index value that is not the uploader's JSON never reaches here: the
 * core reads it as unnumbered, which the null cases stand for.
 */
class EpisodeLabelTest {
    private fun set(kind: Kind, season: Int?, first: Int?, last: Int?) = MediaSet(
        setId = "s", kind = kind, title = "t", show = null, chapter = null, path = null,
        season = season, episodeFirst = first, episodeLast = last, year = null,
        durationSecs = null, posterPath = null, totalBytes = 0,
    )

    @Test
    fun anEpisodeCarriesItsSeason() {
        assertEquals("S2E7", episodeLabel(set(Kind.EPISODE, 2, 7, 7)))
    }

    @Test
    fun episodeZeroIsNotDropped() {
        assertEquals("S2E0", episodeLabel(set(Kind.EPISODE, 2, 0, 0)))
        assertEquals("0", episodeLabel(set(Kind.TUTORIAL, 2, 0, 0)))
    }

    @Test
    fun aRangeIsFirstDashLast() {
        assertEquals("S2E3-4", episodeLabel(set(Kind.EPISODE, 2, 3, 4)))
        assertEquals("3-4", episodeLabel(set(Kind.TUTORIAL, 2, 3, 4)))
    }

    @Test
    fun aRangeOfOneIsASingleNumber() {
        assertEquals("S2E5", episodeLabel(set(Kind.EPISODE, 2, 5, 5)))
    }

    @Test
    fun aLessonHasNoSeasonPrefix() {
        assertEquals("4", episodeLabel(set(Kind.TUTORIAL, 1, 4, 4)))
    }

    @Test
    fun unnumberedIsEmpty() {
        assertEquals("", episodeLabel(set(Kind.MOVIE, null, null, null)))
        assertEquals("", episodeLabel(set(Kind.EPISODE, 2, null, null)))
    }
}
