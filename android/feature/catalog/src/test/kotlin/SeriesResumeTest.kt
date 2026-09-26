package catalog

import model.Kind
import model.MediaSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Mirrors `web/test/series-resume.test.ts`, case for case. */
class SeriesResumeTest {
    private fun ep(
        season: Int,
        episode: Int,
    ): MediaSet =
        MediaSet(
            setId = "s${season}e$episode", kind = Kind.EPISODE, title = "Ep $season.$episode", show = "Show",
            chapter = null, path = null, season = season, episodeFirst = episode, episodeLast = null,
            year = null, durationSecs = null, posterPath = null, totalBytes = 0,
        )

    private val show =
        listOf(
            Division("Season 1", season = 1, items = listOf(ep(1, 1), ep(1, 2)), children = emptyList()),
            Division("Season 2", season = 2, items = listOf(ep(2, 1), ep(2, 2)), children = emptyList()),
        )

    private val none: (String) -> Double? = { null }
    private val nothingWatched: (String) -> Boolean = { false }

    @Test
    fun aFreshShowStartsAtItsFirstEpisode() {
        val pick = seriesResume(show, none, emptyList(), nothingWatched)!!
        assertEquals("s1e1", pick.set.setId)
        assertEquals(ResumeVerb.PLAY, pick.verb)
        assertNull(pick.at)
    }

    @Test
    fun theEpisodeStoppedPartwayThroughWinsEvenBehindAFinishedOne() {
        val pick =
            seriesResume(
                show,
                resumeOf = { if (it == "s1e2") 600.0 else null },
                recent = listOf("other-show-ep", "s1e2"),
                watched = { it == "s2e1" },
            )!!
        assertEquals("s1e2", pick.set.setId)
        assertEquals(ResumeVerb.RESUME, pick.verb)
        assertEquals(600.0, pick.at)
    }

    @Test
    fun otherwiseTheOneAfterTheFurthestFinishedAcrossASeason() {
        val pick = seriesResume(show, none, emptyList()) { it == "s1e1" || it == "s1e2" }!!
        assertEquals("s2e1" to ResumeVerb.CONTINUE, pick.set.setId to pick.verb)
    }

    @Test
    fun aShowWatchedToTheEndOffersItsStartAgain() {
        val pick = seriesResume(show, none, emptyList()) { true }!!
        assertEquals("s1e1" to ResumeVerb.PLAY, pick.set.setId to pick.verb)
    }

    @Test
    fun anEmptyShowOffersNothing() {
        assertNull(seriesResume(emptyList(), none, emptyList(), nothingWatched))
    }

    @Test
    fun theShortLabel() {
        assertEquals("S3 E15", episodeShort(ep(3, 15)))
        assertEquals(
            "Pilot",
            episodeShort(
                MediaSet(
                    setId = "p", kind = Kind.EPISODE, title = "Pilot", show = null, chapter = null, path = null,
                    season = null, episodeFirst = null, episodeLast = null, year = null, durationSecs = null,
                    posterPath = null, totalBytes = 0,
                ),
            ),
        )
    }
}
