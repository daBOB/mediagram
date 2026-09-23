package catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Whether a show gets a wall of seasons or goes straight to its episodes,
 * and what each plate in that wall says about itself.
 */
class SeasonWallTest {

    @Test
    fun aSingleSeasonShowHasNoWall() {
        val show = collection(divisions = listOf(season(1, episodes = 6)))
        assertNull(seasonPlatesOf(show))
    }

    @Test
    fun aCourseIsNeverWalledEvenWithSeveralChapters() {
        val course = collection(
            kind = CollectionKind.COURSE,
            divisions = listOf(season(1, episodes = 2), season(2, episodes = 3)),
        )
        assertNull(seasonPlatesOf(course))
    }

    @Test
    fun oneNumberedSeasonAndOneUnnumberedGetOnePlateEach() {
        val show = collection(
            divisions = listOf(season(1, episodes = 6), unnumbered("Episodes", episodes = 2)),
        )

        val plates = seasonPlatesOf(show)!!
        assertEquals(listOf("Season 1", "Episodes"), plates.map { it.title })
    }

    @Test
    fun captionCountsEveryEpisodeInTheDivision() {
        val show = collection(divisions = listOf(season(1, episodes = 1), season(2, episodes = 4)))

        val plates = seasonPlatesOf(show)!!
        assertEquals("1 episode", plates[0].caption)
        assertEquals("4 episodes", plates[1].caption)
    }

    @Test
    fun aNumberedSeasonsPosterKeyIsDerivedFromTheShowsOwn() {
        val show = collection(
            posterKey = "tmdb-tv-1396",
            divisions = listOf(season(2, episodes = 3), season(3, episodes = 3)),
        )

        val plates = seasonPlatesOf(show)!!
        assertEquals("tmdb-tv-1396-s2", plates[0].posterKey)
        assertEquals("tmdb-tv-1396-s3", plates[1].posterKey)
    }

    @Test
    fun anUnnumberedDivisionHasNoSeasonPosterKeyEvenWithAShowKey() {
        val show = collection(
            posterKey = "tmdb-tv-1396",
            divisions = listOf(season(1, episodes = 3), unnumbered("Specials", episodes = 1)),
        )

        val plates = seasonPlatesOf(show)!!
        assertNull(plates.single { it.title == "Specials" }.posterKey)
    }

    @Test
    fun noShowPosterKeyMeansNoSeasonPosterKeyEither() {
        val show = collection(posterKey = null, divisions = listOf(season(1, episodes = 1), season(2, episodes = 1)))

        val plates = seasonPlatesOf(show)!!
        assertNull(plates[0].posterKey)
    }

    /** A season is watched only once every episode under it is — one straggler keeps the plate untouched. */
    @Test
    fun aSeasonIsWatchedOnlyOnceEveryEpisodeIs() {
        val show = collection(divisions = listOf(season(1, episodes = 2), season(2, episodes = 2)))
        val allOfSeasonOne = show.divisions[0].items.map { it.setId }.toSet()
        val allButOneOfSeasonTwo = show.divisions[1].items.map { it.setId }.drop(1).toSet()

        val plates = seasonPlatesOf(show, watchedIds = allOfSeasonOne + allButOneOfSeasonTwo)!!
        assertEquals(true, plates[0].watched)
        assertEquals(false, plates[1].watched)
    }
}

private fun collection(
    kind: CollectionKind = CollectionKind.SHOW,
    posterKey: String? = null,
    divisions: List<Division>,
) = Entry.Collection(
    key = "$kind/Show",
    kind = kind,
    name = "Show",
    posterPath = null,
    posterKey = posterKey,
    count = divisions.sumOf { it.items.size },
    chapters = divisions.size,
    divisions = divisions,
)

private fun season(number: Int, episodes: Int) = unnumbered("Season $number", episodes, season = number)

private fun unnumbered(title: String, episodes: Int, season: Int? = null) = Division(
    title = title,
    season = season,
    items = (1..episodes).map { fakeSet("$title-$it") },
    children = emptyList(),
)

private fun fakeSet(id: String) = model.MediaSet(
    setId = id,
    kind = model.Kind.EPISODE,
    title = id,
    show = "Show",
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
