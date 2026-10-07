package catalog

import model.Kind
import model.MediaSet
import model.WatchSnapshot
import model.Watched
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The start page answers two questions: what was already underway, and what
 * turned up. A library is added to over years, so the order these rows are
 * in is the whole of what they are for — get it wrong and the page is a
 * handful of shelves with fewer things on them.
 */
class HomeShelvesTest {
    @Test
    fun filmsAreNewestFirst() {
        val latest = latestOf(shelvesOf(listOf(film("Old", at = 100), film("New", at = 900))))

        assertEquals(listOf("New", "Old"), latest.movies.map { it.set.title })
    }

    /**
     * A series still being uploaded keeps its place: it is dated by its
     * newest episode, not by the one that happened to arrive first.
     */
    @Test
    fun aShowIsDatedByItsNewestEpisode() {
        val latest =
            latestOf(
                shelvesOf(
                    listOf(
                        episode("Started long ago", episode = 1, at = 100),
                        episode("Started long ago", episode = 2, at = 5_000),
                        episode("Arrived whole", episode = 1, at = 900),
                    ),
                ),
            )

        assertEquals(
            listOf("Started long ago", "Arrived whole"),
            latest.series.map { it.name },
            "the show that gained an episode most recently leads",
        )
    }

    @Test
    fun aRowHoldsItsLimitAndLeavesTheRestToItsShelf() {
        val many = (1..20).map { film("Film $it", at = it.toLong()) }

        val latest = latestOf(shelvesOf(many))
        assertEquals(HOME_POSTER_ROW_LIMIT, latest.movies.size)
        assertEquals(20, latest.moviesTotal, "the heading counts the whole shelf")
        assertEquals("Film 20", latest.movies.first().set.title, "newest leads")
    }

    /** As the web's `homeShelves` cuts them: a course row is a list and keeps the shorter limit, while series take the poster row's. */
    @Test
    fun coursesTakeTheRowLimitWhileSeriesTakeThePosterLimit() {
        val shows = (1..10).map { episode("Show $it", episode = 1, at = it.toLong()) }
        val courses = (1..10).map { lesson("Course $it", at = it.toLong()) }

        val latest = latestOf(shelvesOf(shows + courses), posterLimit = 8, limit = 6)

        assertEquals(8, latest.series.size)
        assertEquals(6, latest.courses.size)
        assertEquals(10, latest.seriesTotal)
        assertEquals(10, latest.coursesTotal)
    }

    /** An empty library has nothing to say, and says nothing. */
    @Test
    fun anEmptyLibraryHasNothingLatest() {
        assertEquals(Latest(emptyList(), emptyList(), emptyList(), 0, 0, 0), latestOf(emptyList()))
    }

    /**
     * An index that recorded no arrival time still lists. It sorts last
     * rather than first, because an unknown date is not the beginning of
     * time as far as a viewer is concerned.
     */
    @Test
    fun aSetWithNoArrivalTimeStillAppears() {
        val latest = latestOf(shelvesOf(listOf(film("Undated", at = 0), film("Dated", at = 10))))

        assertEquals(listOf("Dated", "Undated"), latest.movies.map { it.set.title })
    }

    /** A finished episode offers the next one under Next up. */
    @Test
    fun aFinishedEpisodeOffersNextUp() {
        val e1 = episode("Show", episode = 1, at = 1)
        val e2 = episode("Show", episode = 2, at = 1)
        val watch = WatchSnapshot.Empty.copy(watched = listOf(Watched(e1.setId, finishedAt = 100)))

        val cards = magazineHomeOf(shelvesOf(listOf(e1, e2)), watch, editorsChoice = null, now = 0).resumeCards

        assertEquals(listOf(e2.setId to "Next up"), cards.map { it.set.setId to it.caption })
    }

    /** An anime series feeds Next up the same way a plain one does — [collectionsForNextUp] walks every shelf but Documentaries, Anime included. */
    @Test
    fun aFinishedAnimeEpisodeOffersNextUpTheSameWayAPlainOneDoes() {
        val e1 = set(Kind.EPISODE, "One", show = "Dragonball", season = 1, episodeFirst = 1, anime = true)
        val e2 = set(Kind.EPISODE, "Two", show = "Dragonball", season = 1, episodeFirst = 2, anime = true)
        val watch = WatchSnapshot.Empty.copy(watched = listOf(Watched(e1.setId, finishedAt = 100)))

        val cards = magazineHomeOf(shelvesOf(listOf(e1, e2)), watch, editorsChoice = null, now = 0).resumeCards

        assertEquals(listOf(e2.setId), cards.map { it.set.setId })
    }

    /** No Latest anime or documentaries — `home-shelves.js` never names either, so this port has no field for them and keeps them off the film row. */
    @Test
    fun animeAndDocumentariesStayOffTheLatestRows() {
        val filmSet = film("Alien", at = 1)
        val animeFilm = set(Kind.MOVIE, "Your Name", addedAt = 2, anime = true)
        val documentary = set(Kind.DOCUMENTARY, "Baraka", addedAt = 3)

        val latest = latestOf(shelvesOf(listOf(filmSet, animeFilm, documentary)))

        assertEquals(listOf("Alien"), latest.movies.map { it.set.title })
        assertEquals(1, latest.moviesTotal)
    }
}

private fun film(
    title: String,
    at: Long,
) = set(Kind.MOVIE, title, addedAt = at)

private fun episode(
    show: String,
    episode: Int,
    at: Long,
) = set(Kind.EPISODE, "Episode $episode of $show", show = show, season = 1, episodeFirst = episode, addedAt = at)

private fun lesson(
    course: String,
    at: Long,
) = set(Kind.TUTORIAL, "Lektion 1", show = course, path = "Grundlagen", addedAt = at)

private fun set(
    kind: Kind,
    title: String,
    show: String? = null,
    path: String? = null,
    season: Int? = null,
    episodeFirst: Int? = null,
    addedAt: Long = 0,
    anime: Boolean = false,
) = MediaSet(
    setId = "$kind-$show-$title",
    kind = kind,
    title = title,
    show = show,
    chapter = null,
    path = path,
    season = season,
    episodeFirst = episodeFirst,
    episodeLast = episodeFirst,
    year = null,
    durationSecs = null,
    posterPath = null,
    totalBytes = 0,
    addedAt = addedAt,
    anime = anime,
)
