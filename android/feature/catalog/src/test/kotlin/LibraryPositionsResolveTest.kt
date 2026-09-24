package catalog

import model.Kind
import model.ListOfSets
import model.MediaSet
import model.WatchSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What [LibraryPositions.resolve] shows when every key is set at once, and
 * what leaving each one in turn uncovers — the priority [LibraryFlow] in
 * ui-mobile used to re-decide at every recomposition, pinned here so a
 * second surface reading the same six keys gets the same answer without
 * rewriting the branch order.
 */
class LibraryPositionsResolveTest {
    @Test
    fun theBackOrderIsPlayerThenMenuThenTitleThenSeasonThenCollectionThenListThenCatalog() {
        val film = filmSet("Alien")
        val episode = episodeSet(show = "Example Show", season = 1, title = "First episode")
        val shelves = shelvesOf(listOf(film, episode))
        val show = shelves.single { it.title == "Series" }.entries.single() as Entry.Collection
        val season = show.divisions.single()
        val list = ListOfSets(id = "list-1", name = "Favourites", items = listOf(film.setId))
        val state = CatalogUiState.Ready(shelves, WatchSnapshot.Empty.copy(collections = listOf(list)))

        val at =
            LibraryPositions(
                setId = film.setId,
                titleId = film.setId,
                collection = show.key,
                season = season.title,
                listId = list.id,
                menuScreen = MenuScreen.System,
            )

        val player = at.resolve(state)
        assertEquals(ResolvedPosition.Player(film.setId), player)
        player.leave(at)

        val menu = at.resolve(state)
        assertEquals(ResolvedPosition.Menu(MenuScreen.System), menu)
        menu.leave(at)

        val title = at.resolve(state)
        assertEquals(ResolvedPosition.TitleOpen(film), title)
        title.leave(at)

        val seasonOpen = at.resolve(state)
        assertEquals(ResolvedPosition.SeasonOpen(season), seasonOpen)
        seasonOpen.leave(at)

        val collectionOpen = at.resolve(state)
        assertEquals(ResolvedPosition.CollectionOpen(show), collectionOpen)
        collectionOpen.leave(at)

        val listOpen = at.resolve(state)
        assertEquals(ResolvedPosition.ListOpen(list), listOpen)
        listOpen.leave(at)

        assertEquals(ResolvedPosition.Catalog, at.resolve(state))
    }

    /**
     * Leaving the collection also drops a season left over from it — the
     * one branch of [leave] that clears two keys. Without it, a stale
     * season key from a show one keystroke away would resolve against
     * whichever collection is opened next, and "Season 1" is not a fact
     * about one show.
     *
     * The stale key here names no division of the opened show — a season
     * only outranks its collection in [LibraryPositions.resolve] when it
     * *does* resolve, so a key this collection cannot match is exactly what
     * leaves the branch shown as [ResolvedPosition.CollectionOpen] with a
     * season key still waiting to be cleared.
     */
    @Test
    fun leavingACollectionAlsoDropsItsSeason() {
        val episode = episodeSet(show = "Example Show", season = 1, title = "First episode")
        val shelves = shelvesOf(listOf(episode))
        val show = shelves.single().entries.single() as Entry.Collection
        val state = CatalogUiState.Ready(shelves)
        val at = LibraryPositions(collection = show.key, season = "Season 99")

        val resolved = at.resolve(state)
        assertEquals(ResolvedPosition.CollectionOpen(show), resolved)
        resolved.leave(at)

        assertEquals(null, at.collection)
        assertEquals(null, at.season)
    }

    private fun filmSet(title: String) = mediaSet(Kind.MOVIE, title)

    private fun episodeSet(
        show: String,
        season: Int,
        title: String,
    ) = mediaSet(Kind.EPISODE, title, show = show, season = season, episodeFirst = 1)

    private fun mediaSet(
        kind: Kind,
        title: String,
        show: String? = null,
        season: Int? = null,
        episodeFirst: Int? = null,
    ) = MediaSet(
        setId = "$kind-$show-$season-$title",
        kind = kind,
        title = title,
        show = show,
        chapter = null,
        path = null,
        season = season,
        episodeFirst = episodeFirst,
        episodeLast = episodeFirst,
        year = null,
        durationSecs = null,
        posterPath = null,
        totalBytes = 0,
        posterKey = null,
    )
}
