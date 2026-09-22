package catalog

import model.Kind
import model.MediaSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The start page answers one question: what turned up. A library is added
 * to over years, so the order these rows are in is the whole of what they
 * are for — get it wrong and the page is three shelves with fewer things
 * on them.
 */
class HomeShelvesTest {

    @Test
    fun filmsAreNewestFirst() {
        val rows = homeRowsOf(shelvesOf(listOf(film("Old", at = 100), film("New", at = 900))))

        val films = rows.single { it.shelf == "Movies" }.entries
        assertEquals(listOf("New", "Old"), films.map { (it as Entry.Film).set.title })
    }

    /**
     * A series still being uploaded keeps its place: it is dated by its
     * newest episode, not by the one that happened to arrive first.
     */
    @Test
    fun aShowIsDatedByItsNewestEpisode() {
        val rows = homeRowsOf(
            shelvesOf(
                listOf(
                    episode("Started long ago", episode = 1, at = 100),
                    episode("Started long ago", episode = 2, at = 5_000),
                    episode("Arrived whole", episode = 1, at = 900),
                ),
            ),
        )

        val shows = rows.single { it.shelf == "Series" }.entries
        assertEquals(
            listOf("Started long ago", "Arrived whole"),
            shows.map { (it as Entry.Collection).name },
            "the show that gained an episode most recently leads",
        )
    }

    @Test
    fun aRowHoldsSixAndLeavesTheRestToItsShelf() {
        val many = (1..20).map { film("Film $it", at = it.toLong()) }

        val row = homeRowsOf(shelvesOf(many)).single()
        assertEquals(HOME_ROW_LIMIT, row.entries.size)
        assertEquals("Film 20", (row.entries.first() as Entry.Film).set.title, "newest leads")
    }

    /** The row names the shelf it is a window onto, so "See all" can open it. */
    @Test
    fun everyRowNamesTheShelfBehindIt() {
        val rows = homeRowsOf(shelvesOf(listOf(film("Alien", at = 1), lesson("Steuerkurs", at = 2))))

        assertEquals(setOf("Movies", "Tutorials"), rows.map { it.shelf }.toSet())
        assertTrue(rows.all { it.title.startsWith("Latest ") }, "rows borrow the shelf's own word")
    }

    /** An empty library has nothing to say, and says nothing. */
    @Test
    fun anEmptyLibraryProducesNoRows() {
        assertTrue(homeRowsOf(emptyList()).isEmpty())
    }

    /**
     * An index that recorded no arrival time still lists. It sorts last
     * rather than first, because an unknown date is not the beginning of
     * time as far as a viewer is concerned.
     */
    @Test
    fun aSetWithNoArrivalTimeStillAppears() {
        val rows = homeRowsOf(shelvesOf(listOf(film("Undated", at = 0), film("Dated", at = 10))))

        val films = rows.single().entries.map { (it as Entry.Film).set.title }
        assertEquals(listOf("Dated", "Undated"), films)
        assertNull(films.firstOrNull { it == "Missing" })
    }
}

private fun film(title: String, at: Long) = set(Kind.MOVIE, title, addedAt = at)

private fun episode(show: String, episode: Int, at: Long) =
    set(Kind.EPISODE, "Episode $episode of $show", show = show, season = 1, episodeFirst = episode, addedAt = at)

private fun lesson(course: String, at: Long) =
    set(Kind.TUTORIAL, "Lektion 1", show = course, path = "Grundlagen", addedAt = at)

private fun set(
    kind: Kind,
    title: String,
    show: String? = null,
    path: String? = null,
    season: Int? = null,
    episodeFirst: Int? = null,
    addedAt: Long = 0,
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
)
