package data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import model.Kind
import settings.InMemoryLibrarySettings
import uniffi.mediagram_core.CreditRecord
import uniffi.mediagram_core.SearchHit
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

    /** A season poster has no set of its own to carry it, so it is asked for by key directly. */
    @Test
    fun posterPathDelegatesToTheCore() =
        runTest {
            val core = FakeCore(posters = mapOf("tmdb-tv-1396-s2" to "/cache/1396-s2.jpg"))
            val repo = DefaultCatalogRepository(ResolvedCoreProvider(core), settingsWithAChosenLibrary(), RefreshLog())

            assertEquals("/cache/1396-s2.jpg", repo.posterPath("tmdb-tv-1396-s2"))
            assertEquals(null, repo.posterPath("tmdb-tv-1396-s9"))
        }

    /**
     * The player asks for one title at a time; walking the whole catalog's
     * worth of poster lookups to answer it would be the same main-thread
     * cost [sets] pays once per rebuild, paid again on every open.
     */
    @Test
    fun mediaSetLooksUpOnlyTheMatchedSetsPoster() = runTest {
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
        assertEquals(listOf("key-2"), core.posterPathCalls)
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

    @Test
    fun creditsResolveEachPersonsPortraitAgainstTheDeviceStore() = runTest {
        val cast = CreditRecord(personId = 5uL, name = "Zendaya", role = "Chani", portraitKey = "tmdb-person-5")
        val crew = CreditRecord(personId = 9uL, name = "Denis Villeneuve", role = "Director", portraitKey = null)
        val core = FakeCore(
            creditsAnswer = TitleCreditsRecord(cast = listOf(cast), crew = listOf(crew)),
            posters = mapOf("tmdb-person-5" to "/cache/person-5.jpg"),
        )
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
