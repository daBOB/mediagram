package catalog

import app.cash.turbine.test
import data.CatalogEnrichmentFetcher
import data.LibraryUpdateCoordinator
import data.LiveSession
import data.settings.InMemoryTmdbSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import model.Kind
import model.Profile
import model.together.Cast
import model.together.CastState
import model.together.Member
import model.together.Session
import org.junit.After
import testing.FakeCore
import testing.FakeCoreProvider
import testing.FakeTogetherLive
import testing.WatchStateFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A cast on another household's TV is that household's: no bar steers it from
 * here and no Join watches along, as the web's `cast-bar.js` and
 * `together-join.js` leave it. Plain watch together is everyone's, as before.
 */
class CatalogCastHouseholdTest {
    private val film = fakeSet(Kind.MOVIE, "film").copy(title = "Film")

    private fun live(cast: Cast?) =
        LiveSession(
            id = 41,
            session = Session("s", "film", true, 0.0, 1.0, 0, "tv", listOf(Member("tv", "TV")), false, cast),
        )

    private fun catalog(together: FakeTogetherLive): CatalogViewModel {
        val repository = FakeCatalogRepository(given = listOf(film))
        val enrichment = CatalogEnrichmentFetcher(FakeCoreProvider(FakeCore()), InMemoryTmdbSettings())
        val watch = WatchStateFixture(listOf(Profile("a", "Ana"))).repository
        return CatalogViewModel(repository, watch, LibraryUpdateCoordinator(repository, enrichment), togetherLive = together, shelving = Dispatchers.Main.immediate)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun aCastOnAnotherHouseholdsTvIsOfferedNothing() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            catalog(FakeTogetherLive(live(Cast(from = "Phone", profile = "m", household = "h2", state = CastState.ON)))).together.test {
                assertNull(awaitItem())
                runCurrent()
                expectNoEvents()
            }
        }

    @Test
    fun plainWatchTogetherIsStillOffered() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            catalog(FakeTogetherLive(live(null))).together.test {
                assertNull(awaitItem())
                assertEquals(film, awaitItem()?.set)
            }
        }
}
