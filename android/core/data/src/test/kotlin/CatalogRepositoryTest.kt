package data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import model.Kind
import settings.InMemoryLibrarySettings
import testing.FakeCore
import testing.ResolvedCoreProvider
import uniffi.mediagram_core.CreditRecord
import uniffi.mediagram_core.SearchHit
import uniffi.mediagram_core.SetSummary
import uniffi.mediagram_core.SubtitleTrack
import uniffi.mediagram_core.TitleCreditsRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogRepositoryTest {
    @Test
    fun refreshBeforeALibraryIsChosenFails() =
        runTest {
            val repo = DefaultCatalogRepository(ResolvedCoreProvider(FakeCore()), InMemoryLibrarySettings(), RefreshLog())
            assertTrue(repo.refresh().isFailure)
        }

    /**
     * The chosen handle is what the refresh is for. A repository that
     * refreshed something else would quietly show one library's catalog
     * under another's name.
     */
    @Test
    fun refreshReadsTheLibraryThisDeviceChose() =
        runTest {
            val core = FakeCore(refreshResult = 12L)
            val repo = DefaultCatalogRepository(ResolvedCoreProvider(core), settingsWithAChosenLibrary("chosen"), RefreshLog())

            assertEquals(12, repo.refresh().getOrThrow())
            assertEquals("chosen", core.refreshedHandle)
        }

    @Test
    fun setsMapKindFromTheCoreSurface() =
        runTest {
            val core = FakeCore(sets = listOf(summary(kind = "ep", episodeFirst = 3, episodeLast = 3)))
            val repo = DefaultCatalogRepository(ResolvedCoreProvider(core), settingsWithAChosenLibrary(), RefreshLog())
            assertEquals(Kind.EPISODE, repo.sets().single().kind)
        }

    /**
     * The core's own listing already carries every set's resolved artwork —
     * [sets] reads it straight off the record, asking the core nothing more
     * per set.
     */
    @Test
    fun setsCarryTheArtworkTheCoreAlreadyResolved() =
        runTest {
            val core = FakeCore(
                sets = listOf(
                    summary(
                        posterPath = "/cache/tmdb-tv-1396.jpg",
                        backdropPath = "/cache/tmdb-tv-1396-bg.jpg",
                        seasonPosterPath = "/cache/tmdb-tv-1396-s2.jpg",
                    ),
                ),
            )
            val repo = DefaultCatalogRepository(ResolvedCoreProvider(core), settingsWithAChosenLibrary(), RefreshLog())

            val set = repo.sets().single()
            assertEquals("/cache/tmdb-tv-1396.jpg", set.posterPath)
            assertEquals("/cache/tmdb-tv-1396-bg.jpg", set.backdropPath)
            assertEquals("/cache/tmdb-tv-1396-s2.jpg", set.seasonPosterPath)
        }

    /**
     * The start page ranks by arrival, so a catalog row that lost its
     * arrival time on the way through would leave every title equally new.
     */
    @Test
    fun aSetKeepsTheTimeItArrived() =
        runTest {
            val core = FakeCore(sets = listOf(summary(kind = "movie", addedAt = 1_781_568_000)))
            val repo = DefaultCatalogRepository(ResolvedCoreProvider(core), settingsWithAChosenLibrary(), RefreshLog())
            assertEquals(1_781_568_000, repo.sets().single().addedAt)
        }

    /**
     * A course handout is a kind of its own, not a lesson and not a film.
     * The shelves place it; this only has to stop calling it nothing.
     */
    @Test
    fun aDocumentKeepsItsOwnKind() =
        runTest {
            val core = FakeCore(sets = listOf(summary(kind = "doc")))
            val repo = DefaultCatalogRepository(ResolvedCoreProvider(core), settingsWithAChosenLibrary(), RefreshLog())
            assertEquals(Kind.DOCUMENT, repo.sets().single().kind)
        }

    /**
     * The index may carry a kind this build has never heard of. It is shown
     * on the film shelf rather than dropped: the wrong shelf is something a
     * viewer can report, an absent title looks like a failed upload.
     */
    @Test
    fun unrecognisedKindIsShelvedWithFilmsRatherThanDropped() =
        runTest {
            val core = FakeCore(sets = listOf(summary(kind = "short-film")))
            val repo = DefaultCatalogRepository(ResolvedCoreProvider(core), settingsWithAChosenLibrary(), RefreshLog())

            val set = repo.sets().single()
            assertEquals(Kind.MOVIE, set.kind)
        }

    /** A thin pass-through: the ranking and the joining both happen above this line. */
    @Test
    fun searchDelegatesToTheCoreUnjoined() = runTest {
        val hit = SearchHit(setId = "set-1", matched = "title", excerpt = null)
        val core = FakeCore(searchHits = listOf(hit))
        val repo = DefaultCatalogRepository(ResolvedCoreProvider(core), settingsWithAChosenLibrary(), RefreshLog())

        assertEquals(listOf(hit), repo.search("steuer"))
        assertEquals("steuer", core.searchedFor)
    }

    /**
     * The player asks for one title at a time; asking the core for the
     * whole catalog to find it would be the same cost [sets] pays once per
     * rebuild, paid again on every open.
     */
    @Test
    fun mediaSetAsksTheCoreForOneSetRatherThanListingEveryOne() = runTest {
        val core = FakeCore(
            sets = listOf(
                summary(setId = "s1", posterKey = "key-1"),
                summary(setId = "s2", posterKey = "key-2"),
                summary(setId = "s3", posterKey = "key-3"),
            ),
        )
        val repo = DefaultCatalogRepository(ResolvedCoreProvider(core), settingsWithAChosenLibrary(), RefreshLog(), dispatcher = Dispatchers.Unconfined)

        val found = repo.mediaSet("s2")

        assertEquals("s2", found?.setId)
        assertEquals(listOf("s2"), core.mediaSetCalls)
        assertEquals(0, core.listSetsCalls, "a single lookup must not fall back to a full listing")
    }

    @Test
    fun mediaSetAnswersNothingForAnUnknownId() = runTest {
        val core = FakeCore(sets = listOf(summary(setId = "s1")))
        val repo = DefaultCatalogRepository(ResolvedCoreProvider(core), settingsWithAChosenLibrary(), RefreshLog(), dispatcher = Dispatchers.Unconfined)

        assertEquals(null, repo.mediaSet("nobody"))
    }

    /** A film's franchise rides in on the same four v9 columns `showStatus` and the rest do. */
    @Test
    fun aSetKeepsItsFranchiseAndSeriesFacts() = runTest {
        val core = FakeCore(
            sets = listOf(
                summary(
                    setId = "dune2",
                    collectionId = 7,
                    collectionName = "Dune Collection",
                    showStatus = "Ended",
                    seriesType = "Scripted",
                ),
            ),
        )
        val repo = DefaultCatalogRepository(ResolvedCoreProvider(core), settingsWithAChosenLibrary(), RefreshLog())

        val set = repo.sets().single()
        assertEquals(7L, set.collectionId)
        assertEquals("Dune Collection", set.collectionName)
        assertEquals("Ended", set.showStatus)
        assertEquals("Scripted", set.seriesType)
    }

    /** A v8 index answers none of the four — `null` throughout, not a mapping failure. */
    @Test
    fun aSetFromAV8IndexHasNoFranchiseOrSeriesFacts() = runTest {
        val core = FakeCore(sets = listOf(summary(setId = "plain")))
        val repo = DefaultCatalogRepository(ResolvedCoreProvider(core), settingsWithAChosenLibrary(), RefreshLog())

        val set = repo.sets().single()
        assertEquals(null, set.collectionId)
        assertEquals(null, set.collectionName)
        assertEquals(null, set.showStatus)
        assertEquals(null, set.seriesType)
    }

    /** The core decides the rule; this only has to carry the boolean through unchanged. */
    @Test
    fun aSetSaysWhetherItIsAnime() = runTest {
        val core = FakeCore(sets = listOf(summary(setId = "spirited-away", anime = true), summary(setId = "dune", anime = false)))
        val repo = DefaultCatalogRepository(ResolvedCoreProvider(core), settingsWithAChosenLibrary(), RefreshLog())

        val sets = repo.sets().associateBy { it.setId }
        assertEquals(true, sets["spirited-away"]?.anime)
        assertEquals(false, sets["dune"]?.anime)
    }

    /** The core decides which unit a set belongs to; this only has to carry its category through unchanged. */
    @Test
    fun aSetCarriesItsCategory() = runTest {
        val core = FakeCore(sets = listOf(summary(setId = "rust-course", category = "Programming"), summary(setId = "dune")))
        val repo = DefaultCatalogRepository(ResolvedCoreProvider(core), settingsWithAChosenLibrary(), RefreshLog())

        val sets = repo.sets().associateBy { it.setId }
        assertEquals("Programming", sets["rust-course"]?.category)
        assertEquals(null, sets["dune"]?.category)
    }

    /** The core resolves a portrait onto the record itself now; this only has to carry it through. */
    @Test
    fun creditsCarryTheirResolvedPortraitPathsThrough() = runTest {
        val cast = CreditRecord(personId = 5uL, name = "Zendaya", role = "Chani", portraitPath = "/cache/person-5.jpg")
        val crew = CreditRecord(personId = 9uL, name = "Denis Villeneuve", role = "Director", portraitPath = null)
        val core = FakeCore(creditsAnswer = TitleCreditsRecord(cast = listOf(cast), crew = listOf(crew)))
        val repo = DefaultCatalogRepository(ResolvedCoreProvider(core), settingsWithAChosenLibrary(), RefreshLog())

        val credits = repo.titleCredits("tmdb-movie-1")
        assertEquals("/cache/person-5.jpg", credits.cast.single().portraitPath)
        assertEquals(null, credits.crew.single().portraitPath)
    }

    @Test
    fun personAnswersNothingForAnUnknownId() = runTest {
        val repo = DefaultCatalogRepository(ResolvedCoreProvider(FakeCore()), settingsWithAChosenLibrary(), RefreshLog())
        assertEquals(null, repo.person(1))
    }

    @Test
    fun fetchPortraitDelegatesStraightToTheCore() = runTest {
        val core = FakeCore(portraits = mapOf(5L to "/cache/person-5.jpg"))
        val repo = DefaultCatalogRepository(ResolvedCoreProvider(core), settingsWithAChosenLibrary(), RefreshLog())
        assertEquals("/cache/person-5.jpg", repo.fetchPortrait(5))
        assertEquals(null, repo.fetchPortrait(6))
    }
}

/** A [SetSummary] with sensible defaults, so a test only names what it cares about. */
private fun summary(
    setId: String = "set-1",
    kind: String = "movie",
    title: String? = "Title",
    show: String? = null,
    chap: String? = null,
    path: String? = null,
    season: Int? = null,
    episodeFirst: Int? = null,
    episodeLast: Int? = null,
    year: Int? = null,
    container: String = "mp4",
    vcodec: String? = null,
    acodec: String? = null,
    quality: String? = null,
    hdr: String? = null,
    duration: Int? = null,
    posterKey: String? = null,
    posterPath: String? = null,
    total: Long = 0L,
    partCount: Int = 1,
    addedAt: Long = 0,
    fsk: String? = null,
    genres: List<String> = emptyList(),
    subtitles: List<SubtitleTrack> = emptyList(),
    alang: List<String> = emptyList(),
    slang: List<String> = emptyList(),
    hasSummary: Boolean = false,
    backdropPath: String? = null,
    seasonPosterPath: String? = null,
    tagline: String? = null,
    rating: Double? = null,
    popularity: Double? = null,
    showStatus: String? = null,
    collectionId: Long? = null,
    collectionName: String? = null,
    seriesType: String? = null,
    anime: Boolean = false,
    category: String? = null,
): SetSummary = SetSummary(
    setId = setId,
    kind = kind,
    title = title,
    show = show,
    chap = chap,
    path = path,
    season = season?.toUInt(),
    episodeFirst = episodeFirst?.toUInt(),
    episodeLast = episodeLast?.toUInt(),
    year = year?.toUInt(),
    container = container,
    vcodec = vcodec,
    acodec = acodec,
    quality = quality,
    hdr = hdr,
    duration = duration?.toUInt(),
    posterKey = posterKey,
    posterPath = posterPath,
    total = total.toULong(),
    partCount = partCount.toUInt(),
    addedAt = addedAt,
    fsk = fsk,
    genres = genres,
    subtitles = subtitles,
    alang = alang,
    slang = slang,
    hasSummary = hasSummary,
    backdropPath = backdropPath,
    seasonPosterPath = seasonPosterPath,
    tagline = tagline,
    rating = rating,
    popularity = popularity,
    showStatus = showStatus,
    collectionId = collectionId?.toULong(),
    collectionName = collectionName,
    seriesType = seriesType,
    anime = anime,
    category = category,
)
