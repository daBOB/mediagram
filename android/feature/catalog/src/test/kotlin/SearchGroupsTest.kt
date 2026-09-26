package catalog

import model.Kind
import model.ListOfSets
import model.MediaSet
import model.PersonHit
import model.WatchSnapshot
import uniffi.mediagram_core.SearchHit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchGroupsTest {
    private fun film(setId: String): MediaSet =
        MediaSet(
            setId = setId, kind = Kind.MOVIE, title = setId, show = null, chapter = null, path = null,
            season = null, episodeFirst = null, episodeLast = null, year = null, durationSecs = null,
            posterPath = null, totalBytes = 0,
        )

    private fun episode(setId: String, show: String): MediaSet =
        MediaSet(
            setId = setId, kind = Kind.EPISODE, title = setId, show = show, chapter = null, path = null,
            season = 1, episodeFirst = 1, episodeLast = null, year = null, durationSecs = null,
            posterPath = null, totalBytes = 0,
        )

    private fun readyState(shelves: List<Shelf>) = CatalogUiState.Ready(shelves, WatchSnapshot.Empty)

    @Test
    fun episodesGroupIntoTheShowTheyBelongToRatherThanListingSeparately() {
        val show = Entry.Collection(
            key = "series/Show", kind = CollectionKind.SHOW, name = "Show", posterPath = null, posterKey = null,
            count = 1, chapters = 1, divisions = listOf(Division("Show", 1, listOf(episode("e1", "Show")), emptyList())),
        )
        val state = readyState(listOf(Shelf("Movies", listOf(Entry.Film(film("f1")))), Shelf("Series", listOf(show))))
        val hits = listOf(SearchHit("f1", "title", null), SearchHit("e1", "title", null))

        val groups = searchGroupsOf("show", state, hits, emptyList(), emptyList(), emptyList())

        assertEquals(listOf("f1"), groups.films.map { it.set.setId })
        assertEquals(listOf("e1"), groups.episodes.map { it.set.setId })
        assertEquals(listOf("series/Show"), groups.matchedShows.map(Entry.Collection::key))
    }

    @Test
    fun peopleAreFilteredToWhatThisProfileCanSeeAndCounted() {
        val state = readyState(listOf(Shelf("Movies", listOf(Entry.Film(film("f1").copy(posterKey = "tmdb-movie-1"))))))
        val hit = PersonHit(1, "Seen", null, listOf("tmdb-movie-1", "tmdb-movie-9"))

        val groups = searchGroupsOf("seen", state, emptyList(), listOf(hit), emptyList(), emptyList())

        assertEquals(1, groups.people.single().titles)
    }

    @Test
    fun collectionsMatchFranchisesAndListsByEveryWordOfTheQuery() {
        val franchise = Franchise(1, "Dune Collection", listOf(film("dune")), null)
        val list = ListOfSets("l1", "Weekend Watch", listOf("f1"))
        val state = readyState(emptyList())

        val groups = searchGroupsOf("dune collection", state, emptyList(), emptyList(), listOf(franchise), listOf(list))
        assertEquals(listOf("Dune Collection"), groups.collections.map(SearchDestination::name))

        val none = searchGroupsOf("weekend", state, emptyList(), emptyList(), listOf(franchise), listOf(list))
        assertEquals(listOf("Weekend Watch"), none.collections.map(SearchDestination::name))
    }

    @Test
    fun filterChipsOmitAllBelowTwoNonEmptyKinds() {
        val state = readyState(listOf(Shelf("Movies", listOf(Entry.Film(film("f1"))))))
        val hits = listOf(SearchHit("f1", "title", null))

        val groups = searchGroupsOf("f", state, hits, emptyList(), emptyList(), emptyList())

        assertEquals(listOf(SearchFilter.MOVIES to 1), groups.filters)
    }

    @Test
    fun filterChipsPrependAllOnceTwoOrMoreKindsHaveResults() {
        val film1 = Entry.Film(film("f1"))
        val list = ListOfSets("l1", "Dune Watch", listOf("f1"))
        val state = readyState(listOf(Shelf("Movies", listOf(film1))))

        val groups = searchGroupsOf("dune", state, listOf(SearchHit("f1", "title", null)), emptyList(), emptyList(), listOf(list))

        assertEquals(SearchFilter.ALL, groups.filters.first().first)
        assertTrue(groups.filters.any { it.first == SearchFilter.MOVIES })
        assertTrue(groups.filters.any { it.first == SearchFilter.COLLECTIONS })
    }
}
