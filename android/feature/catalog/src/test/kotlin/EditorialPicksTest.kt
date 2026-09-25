package catalog

import model.Kind
import model.MediaSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Structural rules [homeEditorial] must hold whatever the seed says — a
 * port of the "the features" and "the cover" `describe` blocks in
 * `web/test/editorial-picks.test.ts`. Exact seeded outputs are
 * [EditorialPicksFixtureTest]'s job; this is everything a fixture cannot
 * show at a glance, like "nothing appears twice."
 */
class EditorialPicksTest {
    private val now = 20_000 * DAY_MS + 3_600_000

    /** A film with artwork, a score and a popularity, unless told otherwise. */
    private fun film(
        setId: String,
        rating: Double? = 6.0,
        popularity: Double? = 10.0,
        addedAt: Long = 1,
        backdropPath: String? = "b-$setId",
        posterPath: String? = "p-$setId",
        tagline: String? = null,
    ): MediaSet =
        MediaSet(
            setId = setId,
            kind = Kind.MOVIE,
            title = setId,
            show = null,
            chapter = null,
            path = null,
            season = null,
            episodeFirst = null,
            episodeLast = null,
            year = null,
            durationSecs = null,
            posterPath = posterPath,
            totalBytes = 0,
            addedAt = addedAt,
            backdropPath = backdropPath,
            tagline = tagline,
            rating = rating,
            popularity = popularity,
        )

    private fun picks(
        movies: List<MediaSet>,
        isWatched: (String) -> Boolean = { false },
        editorsChoice: String? = null,
        onRow: Set<String> = emptySet(),
        now: Long = this.now,
    ): EditorialPicks =
        homeEditorial(movies, movies.associateBy(MediaSet::setId), isWatched, editorsChoice, now, onRow)

    @Test
    fun leadsWithThePinThenTrendingByPopularityThenAStaffPickByScore() {
        val movies =
            listOf(
                film("pinned", rating = 5.0),
                film("popular", popularity = 90.0, rating = 5.0),
                film("acclaimed", rating = 9.0),
                film("plain", rating = 4.0),
            )
        val features = picks(movies, editorsChoice = "pinned").features
        assertEquals(
            listOf(FeatureKind.EDITOR to "pinned", FeatureKind.TRENDING to "popular", FeatureKind.STAFF to "acclaimed"),
            features.map { it.kind to it.set.setId },
        )
    }

    @Test
    fun honoursAPinEvenOnAWatchedTitleWithoutABackdrop() {
        val movies = listOf(film("seen", backdropPath = null), film("a"), film("b"))
        val features = picks(movies, isWatched = { it == "seen" }, editorsChoice = "seen").features
        assertEquals(FeatureKind.EDITOR, features[0].kind)
        assertEquals("seen", features[0].set.setId)
    }

    @Test
    fun withoutAPinTheLeadFollowsTheStaffRuleAndSaysSo() {
        val movies = listOf(film("a", rating = 9.0), film("b", rating = 8.0), film("c", rating = 7.0))
        val features = picks(movies).features
        assertEquals(FeatureKind.STAFF, features[0].kind)
        assertEquals(features.size, features.map { it.set.setId }.toSet().size, "nothing is featured twice")
    }

    @Test
    fun withNoPopularityRecordedTrendingBecomesTheNewestArrivalLabelledAsThat() {
        val movies = listOf(film("old", popularity = null, addedAt = 1), film("fresh", popularity = null, addedAt = 9))
        val result = picks(movies)
        val trending = result.features.find { it.kind == FeatureKind.NEW }
        assertEquals("fresh", trending?.set?.setId)
        assertFalse(result.features.any { it.kind == FeatureKind.TRENDING })
    }

    @Test
    fun neverFeaturesAWatchedFilmOrOneWithNoArtworkExceptByPin() {
        val movies = listOf(film("seen", popularity = 99.0), film("bare", backdropPath = null, posterPath = null, popularity = 98.0), film("ok"))
        val result = picks(movies, isWatched = { it == "seen" })
        val shown = (result.features.map { it.set.setId } + result.cover.map(MediaSet::setId)).toSet()
        assertFalse("seen" in shown)
        assertFalse("bare" in shown)
    }

    @Test
    fun takesAPosterWhenThereIsNoBackdropButTheCoverNeverDoes() {
        val movies = listOf(film("poster-only", backdropPath = null, popularity = 99.0))
        val result = picks(movies)
        assertTrue(result.features.any { it.set.setId == "poster-only" })
        assertEquals(emptyList(), result.cover)
    }

    @Test
    fun coverIsDrawnFromWhatTheFeaturesLeftAndHoldsAtMostFiveFilms() {
        val movies = (0 until 12).map { film("f$it", rating = it.toDouble()) }
        val result = picks(movies)
        assertEquals(COVER_COUNT, result.cover.size)
        val featured = result.features.map { it.set.setId }.toSet()
        assertFalse(result.cover.any { it.setId in featured })
    }

    @Test
    fun coverStaysTheSameAllDaySoARedrawDoesNotReshuffleIt() {
        val movies = (0 until 12).map { film("f$it") }
        val first = picks(movies).cover.map(MediaSet::setId)
        val again = picks(movies, now = now + 3_600_000).cover.map(MediaSet::setId)
        assertEquals(first, again)
    }

    @Test
    fun quotesARealTaglineFromAFilmNotAlreadyFeatured() {
        val movies = listOf(film("a"), film("b"), film("quoted", backdropPath = null, posterPath = null, tagline = "Ein Satz."))
        assertEquals("quoted", picks(movies).quote?.setId)
    }

    @Test
    fun hasNoQuoteWhenNothingCarriesATagline() {
        assertNull(picks(listOf(film("a"))).quote)
    }

    @Test
    fun listsThisMonthsArrivalsNewestFirstAndNothingOlder() {
        val movies =
            listOf(
                film("older", addedAt = now - 40 * DAY_MS),
                film("recent", addedAt = now - 2 * DAY_MS),
                film("newest", addedAt = now - DAY_MS),
            )
        assertEquals(listOf("newest", "recent"), arrivedWithin(movies, now).map(MediaSet::setId))
    }

    @Test
    fun thisMonthListsTheArrivalsAfterTheOnesTheRecentlyAddedRowShows() {
        val movies =
            listOf(
                film("newest", addedAt = now - DAY_MS),
                film("recent", addedAt = now - 2 * DAY_MS),
                film("earlier", addedAt = now - 3 * DAY_MS),
            )
        val result = picks(movies, onRow = setOf("newest", "recent"))
        assertEquals(listOf("earlier"), result.thisMonth.map(MediaSet::setId))
    }

    @Test
    fun theSeededSourceRepeatsForASeedAndDiffersAcrossDays() {
        val first = seededRandom(dayOf(now))
        val again = seededRandom(dayOf(now))
        assertEquals(listOf(first(), first()), listOf(again(), again()))
        assertFalse(seededRandom(1)() == seededRandom(2)())
    }
}
