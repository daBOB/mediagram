package catalog

import model.Kind
import model.MediaSet
import model.Progress
import model.WatchSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [libraryPagesOf]: what each page holds, decided once for both surfaces. `LibraryPagesFixtureTest` holds it to the web. */
class LibraryPagesTest {
    private fun set(
        kind: Kind,
        id: String,
        show: String? = null,
        episode: Int? = null,
        addedAt: Long = 0,
    ) = fakeSet(kind, id).copy(show = show, season = episode?.let { 1 }, episodeFirst = episode, addedAt = addedAt)

    /** The Latest page's one limit is the web's 48 of each kind; Home keeps its own rows — eight posters, six courses. */
    @Test
    fun latestHoldsFortyEightOfEachKindWhileHomeKeepsItsShorterRows() {
        val films = (1..50).map { set(Kind.MOVIE, "film-%02d".format(it), addedAt = it.toLong()) }
        val courses = (1..8).map { set(Kind.TUTORIAL, "lesson-$it", show = "Course $it", episode = 1, addedAt = it.toLong()) }

        val pages = libraryPagesOf(shelvesOf(films + courses), WatchSnapshot.Empty, emptySet(), now = 0)

        assertEquals((50 downTo 3).map { "film-%02d".format(it) }, pages.latest.movies.map { it.set.setId })
        assertEquals(8, pages.latest.courses.size)
        assertEquals((50 downTo 43).map { "film-%02d".format(it) }, pages.home.magazine.recentlyAdded.map(MediaSet::setId))
        assertEquals((8 downTo 3).map { "Course $it" }, pages.home.latest.courses.map { it.name })
    }

    /** What this device holds says "offline" on every resume strip it sits in, Home's and its department's alike. */
    @Test
    fun aHeldTitleCarriesItsBadgeOnEveryStripItIsOn() {
        val episode = set(Kind.EPISODE, "ep", show = "Show", episode = 1)
        val watch = WatchSnapshot.Empty.copy(progress = listOf(Progress("ep", at = 300.0, duration = 1_800.0, updatedAt = 1)))

        val pages = libraryPagesOf(shelvesOf(listOf(episode)), watch, heldIds = setOf("ep"), now = 0)

        assertTrue(pages.home.magazine.resumeCards.single().held)
        assertTrue(assertNotNull(pages.series).resumeCards.single().held)
    }

    /** A department without a shelf has no page; Documentaries, always shelved, has none while it holds nothing. */
    @Test
    fun onlyADepartmentWithSomethingOnItsShelfHasAPage() {
        val pages = libraryPagesOf(shelvesOf(listOf(set(Kind.MOVIE, "film"))), WatchSnapshot.Empty, emptySet(), now = 0)

        assertNotNull(pages.movies)
        assertNull(pages.series)
        assertNull(pages.anime)
        assertNull(pages.documentaries)
        assertNull(pages.tutorials)
    }

    /** The kept walls are the whole of what their tabs promise, resolved against the same shelves as every page. */
    @Test
    fun theKeptWallsResolveTheWatchStateAgainstTheShelves() {
        val films = listOf(set(Kind.MOVIE, "started"), set(Kind.MOVIE, "listed"))
        val watch =
            WatchSnapshot.Empty.copy(
                progress = listOf(Progress("started", at = 600.0, duration = 6_000.0, updatedAt = 1)),
                watchlist = listOf("listed", "gone"),
            )

        val pages = libraryPagesOf(shelvesOf(films), watch, emptySet(), now = 0)

        assertEquals(listOf("started"), pages.continueWall.map(MediaSet::setId))
        assertEquals(listOf("listed"), pages.watchlistWall.map(MediaSet::setId))
    }
}
