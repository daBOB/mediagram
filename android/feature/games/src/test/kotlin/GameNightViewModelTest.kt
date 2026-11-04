package games

import data.DefaultWatchStateRepository
import data.CatalogRepository
import data.settings.InMemoryTmdbSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import model.MediaSet
import org.junit.Rule
import testing.FakeCore
import testing.FakeCoreProvider
import testing.MainDispatcherRule
import uniffi.mediagram_core.SearchHit
import uniffi.mediagram_core.TitleInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class SetsOnly(
    private val sets: List<MediaSet>,
) : CatalogRepository {
    override suspend fun refresh(): Result<Int> = Result.success(sets.size)

    override suspend fun sets(): List<MediaSet> = sets

    override suspend fun search(query: String): List<SearchHit> = emptyList()

    override suspend fun titleInfo(posterKey: String): TitleInfo? = null
}

class GameNightViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val core = FakeCore().apply { profiles = listOf(uniffi.mediagram_core.Profile("a", "Ada")); chosen = "a" }
    private val provider = FakeCoreProvider(core)
    private val watch = DefaultWatchStateRepository(provider, Dispatchers.Unconfined)
    private val tmdb = InMemoryTmdbSettings()

    private fun model(scope: kotlinx.coroutines.CoroutineScope) =
        GameNightViewModel(provider, watch, SetsOnly(listOf(film("f0", "0"), film("f16", "16"))), tmdb, scope)

    @Test
    fun startPassesTheConfigAndShowsTheTvPage() =
        runTest {
            watch.reload()
            tmdb.write("key")
            val model = model(backgroundScope)

            model.start()

            assertEquals(GameNightUi.Ready("http://127.0.0.1:8080/tv"), model.state.first { it !is GameNightUi.Starting })
            val config = core.gameNightStarts.single()
            assertEquals(listOf("f0"), config.kidsSetIds)
            assertEquals("key", config.tmdbKey)
        }

    @Test
    fun failedStartShowsTheMessage() =
        runTest {
            watch.reload()
            core.gameNightStartFailure = IllegalStateException("port taken")
            val model = model(backgroundScope)

            model.start()

            val failed = assertIs<GameNightUi.Failed>(model.state.first { it !is GameNightUi.Starting })
            assertTrue("port taken" in failed.message)
        }

    @Test
    fun stopReachesTheCore() =
        runTest {
            val model = model(kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined))

            model.stop()

            assertEquals(1, core.gameNightStops)
        }
}
