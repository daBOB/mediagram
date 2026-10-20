package player

import data.HouseholdsRepository
import data.LiveSession
import io.mockk.coEvery
import io.mockk.spyk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import model.HOME_HOUSEHOLD
import model.Households
import model.Profile
import model.together.CastWords
import model.together.Member
import model.together.Playback
import model.together.ageReason
import model.together.offered
import model.together.replaced
import model.together.sectionReason
import org.junit.After
import testing.FakeHouseholdsRepository
import testing.FakeTogetherLive
import testing.WatchStateFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The TV answering a cast request once: refused, busy, or accepted and opened. */
class CastReceiverViewModelTest {
    private val tablet = Member("tab-1", "Tablet")
    private val anna = Profile("A", "Anna")
    private val mia = Profile("M", "Mia", kids = true, kidsAge = 6, dailyLimit = 30)
    private val ben = Profile("B", "Ben", hiddenSections = listOf("movies"))

    private val live = FakeTogetherLive()
    private val handle = FakePlayerHandle()
    private val sets = mapOf("film-1" to fakeMediaSet("film-1", title = "Dune"), "film-2" to fakeMediaSet("film-2", fsk = "16"), "film-3" to fakeMediaSet("film-3", fsk = "6"))

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.receiver(
        watch: WatchStateFixture = WatchStateFixture(listOf(anna, mia, ben), chosen = "A"),
        households: HouseholdsRepository = FakeHouseholdsRepository(),
    ): CastReceiverViewModel {
        installMainDispatcher()
        val vm = CastReceiverViewModel(live, FakeCatalogRepository(sets), watch.repository, households, handle)
        vm.opening.launchIn(backgroundScope + UnconfinedTestDispatcher(testScheduler))
        return vm
    }

    private fun request(
        set: String = "film-1",
        profile: String = "A",
    ) = offered("s-cast", tablet, Playback(set, true, pos = 30.0, rate = 1.0), profile, HOME_HOUSEHOLD, System.currentTimeMillis())

    private fun hear(s: model.together.Session) {
        live.requests.value = listOf(LiveSession(11, s))
    }

    @Test
    fun idleTvAcceptsAndOpens() = runTest {
        val vm = receiver()
        hear(request())
        assertEquals(CastOpen("film-1", replace = false), vm.opening.value)
        assertEquals(11, live.takeCast()?.id)
        assertEquals(emptyList(), live.casts)
        vm.opened()
        assertNull(vm.opening.value)
    }

    @Test
    fun busyTvAnswersWithItsTitle() = runTest {
        val vm = receiver()
        handle.openSet.value = "film-1"
        hear(request())
        assertEquals(listOf("busy 11 ${live.casts.single().split(" ")[2]} Dune"), live.casts)
        assertNull(vm.opening.value)
        assertNull(live.takeCast())
    }

    @Test
    fun replaceOpensInsteadOfThePlaying() = runTest {
        val vm = receiver()
        handle.openSet.value = "film-2"
        val offer = request()
        hear(offer)
        hear(replaced(offer, tablet.screen, Playback("film-1", true, 30.0, 1.0), System.currentTimeMillis()))
        assertEquals(CastOpen("film-1", replace = true), vm.opening.value)
    }

    @Test
    fun unknownProfileIsRefused() = runTest {
        receiver()
        hear(request(profile = "Z"))
        assertRefused(CastWords.PROFILE_UNKNOWN)
    }

    @Test
    fun missingTitleIsRefused() = runTest {
        receiver()
        hear(request(set = "gone"))
        assertRefused(CastWords.LIBRARY_MISSING)
    }

    @Test
    fun kidAboveAgeIsRefused() = runTest {
        receiver()
        hear(request(set = "film-2", profile = "M"))
        assertRefused(ageReason("Mia"))
    }

    @Test
    fun aTitleInASectionHiddenFromTheCasterIsRefused() = runTest {
        // A stale tab or a hand-made request: the caster's own catalog would not have offered it.
        receiver()
        hear(request(profile = "B"))
        assertRefused(sectionReason("Ben"))
    }

    @Test
    fun kidOutOfTimeIsRefused() = runTest {
        val watch = WatchStateFixture(listOf(anna, mia), chosen = "A").apply { core.watchedTodayByProfile["M"] = 30 * 60.0 }
        receiver(watch)
        hear(request(set = "film-3", profile = "M"))
        assertRefused(model.together.timeReason("Mia"))
    }

    @Test
    fun aRequestIsAnsweredOnce() = runTest {
        receiver()
        handle.openSet.value = "film-2"
        val offer = request()
        hear(offer)
        hear(offer.copy())
        live.requests.value = listOf(LiveSession(11, offer.copy()))
        assertEquals(1, live.casts.size)
    }

    @Test
    fun aFailedReadDoesNotCrashAndTheNextEmissionIsAnswered() = runTest {
        val watch = WatchStateFixture(listOf(anna, mia), chosen = "A")
        val repo = spyk(watch.repository)
        var reads = 0
        coEvery { repo.watchedToday("M") } coAnswers { if (reads++ == 0) throw IllegalStateException("core down") else 0.0 }
        installMainDispatcher()
        CastReceiverViewModel(live, FakeCatalogRepository(sets), repo, FakeHouseholdsRepository(), handle).opening.launchIn(backgroundScope + UnconfinedTestDispatcher(testScheduler))
        val offer = request(set = "film-3", profile = "M")
        hear(offer)
        assertEquals(emptyList(), live.casts)
        assertNull(live.takeCast())
        hear(offer.copy(at = offer.at + 1))
        assertEquals(11, live.takeCast()?.id)
    }

    @Test
    fun anOpenSetTheCatalogLostIsStillBusy() = runTest {
        val vm = receiver()
        handle.openSet.value = "vanished"
        hear(request())
        assertEquals(true, live.casts.single().endsWith(" vanished"), live.casts.toString())
        assertNull(vm.opening.value)
    }

    @Test
    fun aTvOfAnotherHouseholdSaysNothingNotEvenNo() = runTest {
        // Busy, and with nobody of that name here: at home this TV would answer twice over.
        val vm = receiver(households = FakeHouseholdsRepository("h2"))
        handle.openSet.value = "film-1"
        hear(request(profile = "Z"))
        assertEquals(emptyList(), live.casts)
        assertNull(vm.opening.value)
        assertNull(live.takeCast())
    }

    @Test
    fun anOfferHeardBeforeTheHouseholdsAreReadIsAnsweredOnceTheyAre() = runTest {
        val households = FakeHouseholdsRepository()
        val read = households.view.value
        households.view.value = Households()
        val vm = receiver(households = households)
        hear(request())
        assertNull(vm.opening.value)
        households.view.value = read
        assertEquals(CastOpen("film-1", replace = false), vm.opening.value)
    }

    @Test
    fun aTvInNoHouseholdYetSaysNothing() = runTest {
        val vm = receiver(households = FakeHouseholdsRepository(null))
        hear(request())
        assertEquals(emptyList(), live.casts)
        assertNull(vm.opening.value)
    }

    private fun assertRefused(note: String) {
        val move = live.casts.single()
        assertEquals(true, move.startsWith("refuse 11 "), move)
        assertEquals(true, move.endsWith(" $note"), move)
        assertNull(live.takeCast())
    }
}
