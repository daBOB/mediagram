package catalog

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
        val shelves = listOf(Shelf(Department.MOVIES, listOf(Entry.Film(kept), Entry.Film(started))))
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
    fun departmentCountReadsEachShelfByItsDepartmentAndCollectionsFromTheWatchSnapshot() {
        val shelves =
            listOf(
                Shelf(Department.MOVIES, listOf(Entry.Film(film("A")), Entry.Film(film("B")))),
                Shelf(Department.SERIES, listOf(Entry.Film(film("C")))),
            )
        val watch = WatchSnapshot.Empty.copy(collections = listOf(ListOfSets("l1", "Favourites", emptyList())))

        val counts = chromeCountsOf(shelves, watch)

        assertEquals(2, counts.departmentCount(CatalogTab.Dept(Department.MOVIES)))
        assertEquals(1, counts.departmentCount(CatalogTab.Dept(Department.SERIES)))
        assertEquals(1, counts.departmentCount(CatalogTab.Kept(KeptKind.COLLECTIONS)))
        assertNull(counts.departmentCount(CatalogTab.Home))
        assertNull(counts.departmentCount(CatalogTab.Dept(Department.DOCUMENTARIES)))
    }

    /** Documentaries counts what its folders and singles hold, not one card per folder — the pill names documentaries, not folders. */
    @Test
    fun documentariesCountsItemsInsideFoldersRatherThanTheFoldersThemselves() {
        val folder = Entry.Collection(
            key = "COURSE/Terra X", kind = CollectionKind.COURSE, name = "Terra X", posterPath = null, posterKey = null,
            count = 3, chapters = 1, divisions = listOf(Division("Chapter 1", null, listOf(film("A"), film("B"), film("C")), emptyList())),
        )
        val shelves = listOf(Shelf(Department.DOCUMENTARIES, listOf(folder, Entry.Film(film("Standalone")))))

        assertEquals(4, chromeCountsOf(shelves, WatchSnapshot.Empty).departmentCount(CatalogTab.Dept(Department.DOCUMENTARIES)))
    }

    /** Anime counts its own cards, a show and a film alike, the same as any plain shelf — no special case was needed for it here. */
    @Test
    fun animeCountsItsOwnCardsLikeAnyOtherShelf() {
        val show = Entry.Collection(
            key = "ANIME/Dragonball", kind = CollectionKind.SHOW, name = "Dragonball", posterPath = null, posterKey = null,
            count = 2, chapters = 1, divisions = emptyList(),
        )
        val shelves = listOf(Shelf(Department.ANIME, listOf(show, Entry.Film(film("Your Name")))))

        assertEquals(2, chromeCountsOf(shelves, WatchSnapshot.Empty).departmentCount(CatalogTab.Dept(Department.ANIME)))
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
