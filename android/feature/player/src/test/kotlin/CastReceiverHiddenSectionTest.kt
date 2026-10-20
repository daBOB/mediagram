package player

import data.LiveSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import model.HOME_HOUSEHOLD
import model.Profile
import model.together.Member
import model.together.Playback
import model.together.offered
import model.together.sectionReason
import org.junit.After
import testing.FakeHouseholdsRepository
import testing.FakeTogetherLive
import testing.WatchStateFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The TV refuses a title in a section the caster's profile hides. Their own
 * catalog never offers one, but a stale tab or a hand-made request can.
 */
class CastReceiverHiddenSectionTest {
    private val live = FakeTogetherLive()

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun aTitleInASectionHiddenFromTheCasterIsRefused() =
        runTest {
            installMainDispatcher()
            val ben = Profile("B", "Ben", hiddenSections = listOf("movies"))
            val catalog = FakeCatalogRepository(mapOf("film-1" to fakeMediaSet("film-1")))
            val vm = CastReceiverViewModel(live, catalog, WatchStateFixture(listOf(ben)).repository, FakeHouseholdsRepository(), FakePlayerHandle())
            vm.opening.launchIn(backgroundScope + UnconfinedTestDispatcher(testScheduler))

            val offer = offered("s-cast", Member("tab-1", "Tablet"), Playback("film-1", true, 30.0, 1.0), "B", HOME_HOUSEHOLD, System.currentTimeMillis())
            live.requests.value = listOf(LiveSession(11, offer))

            val move = live.casts.single()
            assertEquals(true, move.startsWith("refuse 11 ") && move.endsWith(" ${sectionReason("Ben")}"), move)
            assertNull(vm.opening.value)
        }
}
