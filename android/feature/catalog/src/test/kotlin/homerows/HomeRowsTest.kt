package homerows

import model.Kind
import model.MediaSet
import model.Profile
import model.Progress
import model.WatchSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The rows the TV home screen shows for whoever is watching, held to what that person may see. */
class HomeRowsTest {
    private val grownUp = Profile(id = "p1", name = "Nadin")

    @Test
    fun nobodyChosenGivesNoPersonAndNoRows() {
        val sets = listOf(film("A"))
        assertEquals(HomeRows.None, homeRowsOf(null, sets, watch(progress = listOf(progress("movie-A", 1_800.0, 20)))))
    }

    @Test
    fun continueFollowsContinueWallOrderWithPosition() {
        val sets = listOf(film("Old"), film("New"))
        val rows =
            homeRowsOf(
                grownUp,
                sets,
                watch(progress = listOf(progress("movie-Old", 1_800.0, 10), progress("movie-New", 600.0, 20))),
            )

        assertEquals(HomePerson("p1", "Nadin", kids = false), rows.person)
        assertEquals(listOf("New", "Old"), rows.continuing.map(HomeTitle::title))
        assertEquals(600.0, rows.continuing.first().positionSecs)
        assertEquals(20L, rows.continuing.first().updatedAt)
    }

    @Test
    fun myListFollowsWatchlistWall() {
        val sets = listOf(film("First"), film("Second"))
        val rows = homeRowsOf(grownUp, sets, watch(watchlist = listOf("movie-Second", "gone", "movie-First")))

        assertEquals(listOf("Second", "First"), rows.myList.map(HomeTitle::title))
        assertNull(rows.myList.first().positionSecs)
    }

    @Test
    fun aKidSeesOnlyTitlesOnTheirShelves() {
        val kid = Profile(id = "k1", name = "Lia", kids = true, kidsAge = 6)
        val sets = listOf(film("Safe", fsk = "0"), film("Adult", fsk = "18"))
        val rows =
            homeRowsOf(
                kid,
                sets,
                watch(
                    progress = listOf(progress("movie-Safe", 1_800.0, 10), progress("movie-Adult", 1_800.0, 20)),
                    watchlist = listOf("movie-Adult", "movie-Safe"),
                ),
            )

        assertEquals(listOf("Safe"), rows.continuing.map(HomeTitle::title))
        assertEquals(listOf("Safe"), rows.myList.map(HomeTitle::title))
        assertEquals(setOf("movie-Safe"), rows.imageIds)
        assertTrue(rows.person!!.kids)
    }

    @Test
    fun seriesCardCarriesShowSeasonAndEpisode() {
        val sets = listOf(episode("Dark", season = 2, number = 5))
        val rows = homeRowsOf(grownUp, sets, watch(progress = listOf(progress("ep-Dark-2-5", 1_200.0, 10))))

        val card = rows.continuing.single()
        assertEquals("Dark", card.show)
        assertEquals(2, card.season)
        assertEquals(5, card.episode)
        assertEquals(Kind.EPISODE.name, card.kind)
    }
}

private fun watch(
    progress: List<Progress> = emptyList(),
    watchlist: List<String> = emptyList(),
) = WatchSnapshot(progress = progress, watched = emptyList(), watchlist = watchlist, kids = emptyList(), collections = emptyList())

private fun progress(
    setId: String,
    at: Double,
    updatedAt: Long,
) = Progress(setId, at, 3_600.0, updatedAt)

private fun film(
    title: String,
    fsk: String? = null,
) = MediaSet(
    setId = "movie-$title",
    kind = Kind.MOVIE,
    title = title,
    show = null,
    chapter = null,
    path = null,
    season = null,
    episodeFirst = null,
    episodeLast = null,
    year = 2020,
    durationSecs = 3_600,
    posterPath = null,
    totalBytes = 0,
    fsk = fsk,
)

private fun episode(
    show: String,
    season: Int,
    number: Int,
) = MediaSet(
    setId = "ep-$show-$season-$number",
    kind = Kind.EPISODE,
    title = "$show $number",
    show = show,
    chapter = null,
    path = null,
    season = season,
    episodeFirst = number,
    episodeLast = number,
    year = null,
    durationSecs = 3_600,
    posterPath = null,
    totalBytes = 0,
)
