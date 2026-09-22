package data

import kotlinx.coroutines.test.runTest
import model.Kind
import settings.InMemoryLibrarySettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogRepositoryTest {

    @Test
    fun refreshBeforeALibraryIsChosenFails() = runTest {
        val repo = DefaultCatalogRepository(ResolvedCoreProvider(FakeCore()), InMemoryLibrarySettings(), RefreshLog())
        assertTrue(repo.refresh().isFailure)
    }

    /**
     * The chosen handle is what the refresh is for. A repository that
     * refreshed something else would quietly show one library's catalog
     * under another's name.
     */
    @Test
    fun refreshReadsTheLibraryThisDeviceChose() = runTest {
        val core = FakeCore(refreshResult = 12L)
        val repo = DefaultCatalogRepository(ResolvedCoreProvider(core), settingsWithAChosenLibrary("chosen"), RefreshLog())

        assertEquals(12, repo.refresh().getOrThrow())
        assertEquals("chosen", core.refreshedHandle)
    }

    @Test
    fun setsMapKindFromTheCoreSurface() = runTest {
        val core = FakeCore(sets = listOf(summary(kind = "ep", episodeFirst = 3, episodeLast = 3)))
        val repo = DefaultCatalogRepository(ResolvedCoreProvider(core), settingsWithAChosenLibrary(), RefreshLog())
        assertEquals(Kind.EPISODE, repo.sets().single().kind)
    }

    /**
     * A course handout is a kind of its own, not a lesson and not a film.
     * The shelves place it; this only has to stop calling it nothing.
     */
    @Test
    fun aDocumentKeepsItsOwnKind() = runTest {
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
    fun unrecognisedKindIsShelvedWithFilmsRatherThanDropped() = runTest {
        val core = FakeCore(sets = listOf(summary(kind = "short-film")))
        val repo = DefaultCatalogRepository(ResolvedCoreProvider(core), settingsWithAChosenLibrary(), RefreshLog())

        val set = repo.sets().single()
        assertEquals(Kind.MOVIE, set.kind)
    }
}
