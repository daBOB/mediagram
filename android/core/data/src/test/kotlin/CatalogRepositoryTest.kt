package data

import kotlinx.coroutines.test.runTest
import model.Kind
import settings.InMemoryPackageSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogRepositoryTest {

    @Test
    fun refreshWithoutCredentialsFails() = runTest {
        val repo = DefaultCatalogRepository(FakeCore(), InMemoryPackageSettings())
        assertTrue(repo.refresh().isFailure)
    }

    @Test
    fun setsMapKindFromTheCoreSurface() = runTest {
        val core = FakeCore(sets = listOf(summary(kind = "ep", episodeFirst = 3, episodeLast = 3)))
        val repo = DefaultCatalogRepository(core, settingsWithCredentials())
        assertEquals(Kind.EPISODE, repo.sets().single().kind)
    }

    @Test
    fun unrecognisedKindIsDroppedRatherThanCrashing() = runTest {
        val core = FakeCore(sets = listOf(summary(kind = "short-film")))
        val repo = DefaultCatalogRepository(core, settingsWithCredentials())
        assertTrue(repo.sets().isEmpty())
    }
}
