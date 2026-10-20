package player

import data.CoreTogetherChannel
import data.DefaultTogetherLive
import data.LibraryEvents
import data.settings.InMemoryLibrarySettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import model.HOME_HOUSEHOLD
import model.Profile
import model.together.Member
import model.together.Playback
import model.together.offered
import model.together.writeSession
import org.junit.After
import testing.FakeCore
import testing.FakeCoreProvider
import testing.FakeHouseholdsRepository
import testing.WatchStateFixture
import uniffi.mediagram_core.LibraryEvent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Two households' TVs hear each other's offers, over the real
 * `DefaultTogetherLive`: another household's, landing beside this TV's own,
 * never hides it.
 */
class CastReceiverHouseholdsTest {
    private val tablet = Member("tab-1", "Tablet")

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun anotherHouseholdsOfferReadBesideThisOnesDoesNotHideIt() =
        runTest {
            installMainDispatcher()
            val core = FakeCore()
            val events = MutableSharedFlow<LibraryEvent>()
            val settings = InMemoryLibrarySettings().apply { runBlocking { write("library") } }
            val live = DefaultTogetherLive(CoreTogetherChannel(FakeCoreProvider(core), settings, LibraryEvents { events }), backgroundScope, durationOf = { null })
            val now = System.currentTimeMillis()
            core.togetherMessages[11] = writeSession(offered("s-home", tablet, Playback("film-1", true, 30.0, 1.0), "A", HOME_HOUSEHOLD, now))
            core.togetherMessages[12] = writeSession(offered("s-away", tablet, Playback("film-2", true, 30.0, 1.0), "A", "h2", now))
            val watch = WatchStateFixture(listOf(Profile("A", "Anna")), chosen = "A")
            val catalog = FakeCatalogRepository(mapOf("film-1" to fakeMediaSet("film-1"), "film-2" to fakeMediaSet("film-2")))
            val vm = CastReceiverViewModel(live, catalog, watch.repository, FakeHouseholdsRepository(), FakePlayerHandle())
            vm.opening.launchIn(backgroundScope + UnconfinedTestDispatcher(testScheduler))

            // Both in one read, as a TV just opened finds them.
            live.refresh()
            runCurrent()

            assertEquals(CastOpen("film-1", replace = false), vm.opening.value)
        }
}
