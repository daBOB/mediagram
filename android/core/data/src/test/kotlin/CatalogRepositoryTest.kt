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
        val repo = DefaultCatalogRepository(ResolvedCoreProvider(FakeCore()), InMemoryLibrarySettings())
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
        val repo = DefaultCatalogRepository(ResolvedCoreProvider(core), settingsWithAChosenLibrary("chosen"))

        assertEquals(12, repo.refresh().getOrThrow())
        assertEquals("chosen", core.refreshedHandle)
    }

    @Test
    fun setsMapKindFromTheCoreSurface() = runTest {
        val core = FakeCore(sets = listOf(summary(kind = "ep", episodeFirst = 3, episodeLast = 3)))
        val repo = DefaultCatalogRepository(ResolvedCoreProvider(core), settingsWithAChosenLibrary())
        assertEquals(Kind.EPISODE, repo.sets().single().kind)
    }

    @Test
    fun unrecognisedKindIsDroppedRatherThanCrashing() = runTest {
        val core = FakeCore(sets = listOf(summary(kind = "short-film")))
        val repo = DefaultCatalogRepository(ResolvedCoreProvider(core), settingsWithAChosenLibrary())
        assertTrue(repo.sets().isEmpty())
    }
}
