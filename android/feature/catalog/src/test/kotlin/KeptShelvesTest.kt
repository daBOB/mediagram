package catalog

import model.Kind
import model.MediaSet
import model.Progress
import model.WatchSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The four kept walls — Continue, Watchlist, Collections, Kids — against the
 * ordering and dropping rules `app.js`'s `KEPT` shelves already keep. The
 * core answers Watchlist and Kids pre-ordered (`WatchStateRepositoryTest`
 * covers that SQL); what belongs here is what this module adds on top:
 * resolving an id back to a set, and dropping one the catalog no longer
 * holds.
 */
class KeptShelvesTest {

    @Test
    fun continueIsNewestTouchedFirst() {
        val old = film("Old")
        val new = film("New")
        val watch = watchOf(
            progress(old, at = 1_800.0, duration = 3_600.0, updatedAt = 10),
            progress(new, at = 1_800.0, duration = 3_600.0, updatedAt = 20),
        )

        val wall = continueWall(shelvesOf(listOf(old, new)), watch)
        assertEquals(listOf("New", "Old"), wall.map(MediaSet::title))
    }

    @Test
    fun continueDropsAGlanceAndAFinish() {
        val glanced = film("Glanced")
        val finished = film("Finished")
        val watch = watchOf(
            // Under the thirty-second floor: opened, not watched.
            progress(glanced, at = 5.0, duration = 3_600.0, updatedAt = 10),
            // Inside the credits tail: as good as finished.
            progress(finished, at = 3_595.0, duration = 3_600.0, updatedAt = 20),
        )

        assertTrue(continueWall(shelvesOf(listOf(glanced, finished)), watch).isEmpty())
    }

    @Test
    fun watchlistDropsATitleTheCatalogNoLongerHolds() {
        val kept = film("Kept")
        val watch = snapshotOf(watchlist = listOf(kept.setId, "gone-set-id"))

        val wall = watchlistWall(shelvesOf(listOf(kept)), watch)
        assertEquals(listOf(kept.setId), wall.map(MediaSet::setId))
    }

    @Test
    fun watchlistKeepsTheCoresOwnOrder() {
        val first = film("First added")
        val second = film("Second added")
        // The core orders newest-added first; this only has to carry that
        // order through, not invent one of its own.
        val watch = snapshotOf(watchlist = listOf(second.setId, first.setId))

        val wall = watchlistWall(shelvesOf(listOf(first, second)), watch)
        assertEquals(listOf(second.setId, first.setId), wall.map(MediaSet::setId))
    }

    @Test
    fun kidsDropsATitleTheCatalogNoLongerHolds() {
        val kept = film("Kept")
        val watch = snapshotOf(kids = listOf("gone-set-id", kept.setId))

        assertEquals(listOf(kept.setId), kidsWall(shelvesOf(listOf(kept)), watch).map(MediaSet::setId))
    }

    @Test
    fun labelsAndEmptyTextMatchTheWebsKept() {
        assertEquals("Continue" to "Nothing started yet.", KeptKind.CONTINUE.label to KeptKind.CONTINUE.empty)
        assertEquals("Watchlist" to "Nothing on the list.", KeptKind.WATCHLIST.label to KeptKind.WATCHLIST.empty)
        assertEquals("Collections" to "No lists yet.", KeptKind.COLLECTIONS.label to KeptKind.COLLECTIONS.empty)
        assertEquals(
            "Kids" to "Nothing marked yet. Open a title and press Kids in the player.",
            KeptKind.KIDS.label to KeptKind.KIDS.empty,
        )
    }
}

private fun film(title: String) = MediaSet(
    setId = "movie-$title",
    kind = Kind.MOVIE,
    title = title,
    show = null,
    chapter = null,
    path = null,
    season = null,
    episodeFirst = null,
    episodeLast = null,
    year = null,
    durationSecs = null,
    posterPath = null,
    totalBytes = 0,
)

private fun watchOf(vararg rows: Progress) = WatchSnapshot(
    progress = rows.toList(),
    watched = emptyList(),
    watchlist = emptyList(),
    kids = emptyList(),
    collections = emptyList(),
)

private fun snapshotOf(watchlist: List<String> = emptyList(), kids: List<String> = emptyList()) = WatchSnapshot(
    progress = emptyList(),
    watched = emptyList(),
    watchlist = watchlist,
    kids = kids,
    collections = emptyList(),
)

private fun progress(set: MediaSet, at: Double, duration: Double, updatedAt: Long) =
    Progress(set.setId, at, duration, updatedAt)
