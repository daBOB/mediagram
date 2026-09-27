package ui.chrome

import catalog.Entry
import catalog.Shelf
import model.Kind
import model.ListOfSets
import model.MediaSet
import model.WatchSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChromeCountsTest {
    @Test
    fun myListAndContinueReadTheKeptWallsSizes() {
        val kept = film("Kept")
        val started = film("Started")
        val shelves = listOf(Shelf("Movies", listOf(Entry.Film(kept), Entry.Film(started))))
        val watch =
            WatchSnapshot.Empty.copy(
                watchlist = listOf(kept.setId),
                progress = listOf(model.Progress(started.setId, at = 120.0, duration = 3_600.0, updatedAt = 1)),
            )

        val counts = chromeCountsOf(shelves, watch)

        assertEquals(1, counts.myList)
        assertEquals(1, counts.continueWatching)
    }

    @Test
    fun departmentCountReadsEachShelfByItsTitleAndCollectionsFromTheWatchSnapshot() {
        val shelves =
            listOf(
                Shelf("Movies", listOf(Entry.Film(film("A")), Entry.Film(film("B")))),
                Shelf("Series", listOf(Entry.Film(film("C")))),
            )
        val watch = WatchSnapshot.Empty.copy(collections = listOf(ListOfSets("l1", "Favourites", emptyList())))

        val counts = chromeCountsOf(shelves, watch)

        assertEquals(2, counts.departmentCount("Movies"))
        assertEquals(1, counts.departmentCount("Series"))
        assertEquals(1, counts.departmentCount("Collections"))
        assertNull(counts.departmentCount("Home"))
        assertNull(counts.departmentCount("Documentaries"))
    }

    @Test
    fun profileInitialUppercasesTheFirstLetterOrFallsBackToAQuestionMark() {
        assertEquals("T", profileInitial("test"))
        assertEquals("T", profileInitial("  test"))
        assertEquals("?", profileInitial(""))
        assertEquals("?", profileInitial("   "))
    }
}

private fun film(title: String) =
    MediaSet(
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
