package catalog

import model.Kind
import model.MediaSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Mirrors `web/test/visible-people.test.ts`, case for case. */
class VisiblePeopleTest {
    @Test
    fun searchKeepsOnlyPeopleCreditedOnATitleThisProfileCanSeeCountedByThose() {
        val visible = setOf("tmdb-movie-1")
        val people =
            listOf(
                PersonCandidate(1, "Seen", null, listOf("tmdb-movie-1", "tmdb-movie-2")),
                PersonCandidate(2, "Hidden", null, listOf("tmdb-movie-2")),
            )
        val kept = visiblePeople(people) { it in visible }
        assertEquals(listOf("Seen" to 1), kept.map { it.name to it.titles })
    }

    @Test
    fun titlesByKeyIsVisibleForAFilmsOrAShowsFirstEpisodesOwnKeyOnly() {
        val film = MediaSet(
            setId = "f1", kind = Kind.MOVIE, title = "f1", show = null, chapter = null, path = null,
            season = null, episodeFirst = null, episodeLast = null, year = null, durationSecs = null,
            posterPath = null, totalBytes = 0, posterKey = "tmdb-movie-1",
        )
        val episode = MediaSet(
            setId = "e1", kind = Kind.EPISODE, title = "E1", show = "Show", chapter = null, path = null,
            season = 1, episodeFirst = 1, episodeLast = null, year = null, durationSecs = null,
            posterPath = null, totalBytes = 0, posterKey = "tmdb-tv-1",
        )
        val show = Entry.Collection(
            key = "series/Show", kind = CollectionKind.SHOW, name = "Show", posterPath = null, posterKey = "tmdb-tv-1",
            count = 1, chapters = 1, divisions = listOf(Division("Show", 1, listOf(episode), emptyList())),
        )
        val shelves = listOf(Shelf("Movies", listOf(Entry.Film(film))), Shelf("Series", listOf(show)))
        val isVisible = titlesByKey(shelves)

        assertTrue(isVisible("tmdb-movie-1"))
        assertTrue(isVisible("tmdb-tv-1"))
        assertFalse(isVisible("tmdb-movie-9"))
    }
}
