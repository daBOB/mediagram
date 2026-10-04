package catalog

import model.Kind
import model.MediaSet
import model.Progress
import model.WatchSnapshot
import model.Watched
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [groupAnime] and [animeDepartmentOf] — the same split and department shape
 * [ShelvesTest]/[DepartmentsTest] pin for the plain shelves, over sets
 * already marked [MediaSet.anime].
 */
class AnimeTest {
    @Test
    fun episodesGroupByShowAndFilmsStayAsCards() {
        val library = groupAnime(
            listOf(
                episode("Dragonball", season = 1, episode = 1, title = "One"),
                episode("Dragonball", season = 1, episode = 2, title = "Two"),
                film("Your Name"),
            ),
        )

        assertEquals(listOf("Dragonball"), library.shows.map { it.name })
        assertEquals(2, library.shows.single().count)
        assertEquals(listOf("Your Name"), library.films.map { it.title })
    }

    /** Re-keyed under its own `ANIME/` prefix, same reason [Documentaries] re-keys its own folders. */
    @Test
    fun aShowIsKeyedUnderItsOwnAnimePrefix() {
        val library = groupAnime(listOf(episode("Dragonball", season = 1, episode = 1, title = "One")))

        assertEquals("ANIME/Dragonball", library.shows.single().key)
    }

    @Test
    fun anAnimeShowWithNoNameIsStillReachable() {
        val library = groupAnime(listOf(episode(show = null, season = 1, episode = 1, title = "Orphan")))

        assertEquals("Unknown show", library.shows.single().name)
    }

    @Test
    fun anEmptyLibraryHasNoDepartment() {
        assertNull(animeDepartmentOf(AnimeLibrary(emptyList(), emptyList()), emptyMap(), WatchSnapshot.Empty))
    }

    @Test
    fun theLeadIsTheMostPopularUnwatchedFilmOrShowLeadWithABackdrop() {
        val library = groupAnime(
            listOf(
                episode("No Art", season = 1, episode = 1, title = "One", popularity = 99.0),
                episode("Lead Show", season = 1, episode = 1, title = "One", popularity = 10.0, backdrop = "show-bg"),
                film("Watched Favourite", popularity = 200.0, backdrop = "wf-bg"),
            ),
        )
        val watch = WatchSnapshot.Empty.copy(watched = listOf(Watched("movie-Watched Favourite", finishedAt = 1)))

        val dept = animeDepartmentOf(library, emptyMap(), watch)!!

        assertEquals("Lead Show", dept.lead?.show)
    }

    /**
     * The general Continue list resolves any started title through [byId],
     * regardless of kind — an anime film is a plain [Kind.MOVIE], so only
     * [MediaSet.anime] tells it apart from a Movies-shelf title sharing the
     * same watch snapshot.
     */
    @Test
    fun continuingIsNarrowedToAnimeSetsRatherThanEveryStartedTitle() {
        val animeFilm = film("Anime Film")
        val plainFilm = MediaSet(
            setId = "movie-Plain Film", kind = Kind.MOVIE, title = "Plain Film", show = null, chapter = null,
            path = null, season = null, episodeFirst = null, episodeLast = null, year = null, durationSecs = null,
            posterPath = null, totalBytes = 0, anime = false,
        )
        val byId = mapOf(animeFilm.setId to animeFilm, plainFilm.setId to plainFilm)
        val watch = WatchSnapshot.Empty.copy(
            progress = listOf(
                Progress(animeFilm.setId, at = 10.0, duration = 100.0, updatedAt = 1),
                Progress(plainFilm.setId, at = 10.0, duration = 100.0, updatedAt = 2),
            ),
        )

        val dept = animeDepartmentOf(AnimeLibrary(emptyList(), listOf(animeFilm)), byId, watch)!!

        assertEquals(listOf(animeFilm.setId), dept.continuing.map { it.setId })
    }

    @Test
    fun filmsAreNewestFirst() {
        val library = AnimeLibrary(emptyList(), listOf(film("Old", addedAt = 1), film("New", addedAt = 2)))

        val dept = animeDepartmentOf(library, emptyMap(), WatchSnapshot.Empty)!!

        assertEquals(listOf("New", "Old"), dept.films.map { it.title })
    }

    @Test
    fun showCountAndFilmCountReadTheLibraryAsAWhole() {
        val library = groupAnime(
            listOf(episode("Show A", season = 1, episode = 1, title = "One"), film("Film A"), film("Film B")),
        )

        val dept = animeDepartmentOf(library, emptyMap(), WatchSnapshot.Empty)!!

        assertEquals(1, dept.showCount)
        assertEquals(2, dept.filmCount)
        assertTrue(dept.nextUp.isEmpty(), "nothing watched yet, so nothing is next")
    }

    @Test
    fun anAnimeFilmInProgressStaysOnTheRowHoweverManyOtherFilmsWereStartedSince() {
        val animeFilm = film("Anime Film")
        val others = (1..DEPARTMENT_ROW + 1).map { film("Plain $it").copy(anime = false) }
        val byId = (others + animeFilm).associateBy(MediaSet::setId)
        val watch = WatchSnapshot.Empty.copy(
            progress = listOf(Progress(animeFilm.setId, at = 10.0, duration = 100.0, updatedAt = 1)) +
                others.mapIndexed { i, one -> Progress(one.setId, at = 10.0, duration = 100.0, updatedAt = 2L + i) },
        )

        val dept = animeDepartmentOf(AnimeLibrary(emptyList(), listOf(animeFilm)), byId, watch)!!

        assertEquals(listOf(animeFilm.setId), dept.continuing.map { it.setId })
    }

    private fun film(
        title: String,
        popularity: Double? = null,
        backdrop: String? = null,
        addedAt: Long = 0,
    ) = MediaSet(
        setId = "movie-$title", kind = Kind.MOVIE, title = title, show = null, chapter = null, path = null,
        season = null, episodeFirst = null, episodeLast = null, year = null, durationSecs = null,
        posterPath = null, totalBytes = 0, popularity = popularity, backdropPath = backdrop, addedAt = addedAt,
        anime = true,
    )

    private fun episode(
        show: String?,
        season: Int,
        episode: Int,
        title: String,
        popularity: Double? = null,
        backdrop: String? = null,
    ) = MediaSet(
        setId = "ep-$show-$season-$episode", kind = Kind.EPISODE, title = title, show = show, chapter = null,
        path = null, season = season, episodeFirst = episode, episodeLast = episode, year = null, durationSecs = null,
        posterPath = null, totalBytes = 0, popularity = popularity, backdropPath = backdrop,
        anime = true,
    )
}
