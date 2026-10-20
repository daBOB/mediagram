package player

import data.CastAnswer
import data.CastAnswer.Answer
import data.HouseholdsRepository
import data.LiveSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import model.Profile
import model.together.CastWords
import model.together.Member
import model.together.Playback
import model.together.offered
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import testing.FakeHouseholdsRepository
import testing.FakeTogetherLive
import testing.WatchStateFixture
import kotlin.test.Test
import kotlin.test.assertEquals

/** Play on TV asks only a TV of this device's household, and a device in none yet asks no TV at all. */
@RunWith(RobolectricTestRunner::class)
class CastSenderHouseholdTest {
    private val own = Playback("film-1", playing = true, pos = 30.0, rate = 1.0)
    private val live = FakeTogetherLive()

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.sender(households: HouseholdsRepository): CastSenderViewModel {
        installMainDispatcher()
        val watch = WatchStateFixture(listOf(Profile("A", "Anna")), chosen = "A")
        return CastSenderViewModel(live, watch.repository, households, RuntimeEnvironment.getApplication())
    }

    @Test
    fun theOfferIsForATvOfThisDevicesHousehold() = runTest {
        val vm = sender(FakeHouseholdsRepository("h2"))
        live.onOffer = { CastAnswer(Answer.ON, LiveSession(11, offered("s", Member("tab", "Tablet", remote = true), own, "A", "h2", 0L)), "") }
        vm.playOnTv(own)
        assertEquals(listOf("offer Phone film-1 A h2"), live.casts)
        assertEquals(CastAsk.On(11), vm.ask.value)
    }

    @Test
    fun aDeviceInNoHouseholdYetOffersNoTv() = runTest {
        val vm = sender(FakeHouseholdsRepository(null))
        assertEquals(CastWords.JOIN_FIRST, vm.castable.value)
        vm.playOnTv(own)
        assertEquals(emptyList(), live.casts)
    }
}
