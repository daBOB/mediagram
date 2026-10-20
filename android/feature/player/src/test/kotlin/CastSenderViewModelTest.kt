package player

import androidx.lifecycle.ViewModelStore
import data.CastAnswer
import data.CastAnswer.Answer
import data.LiveSession
import data.TOGETHER_UNREACHABLE
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import model.HOME_HOUSEHOLD
import model.Profile
import model.together.CAST_ANSWER_MS
import model.together.CastWords
import model.together.Member
import model.together.Playback
import model.together.offered
import model.together.refusedLine
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import testing.FakeHouseholdsRepository
import testing.FakeTogetherLive
import testing.WatchStateFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Pressing Play on TV: the offer, the wait, and each answer the TV can give. */
@RunWith(RobolectricTestRunner::class)
class CastSenderViewModelTest {
    private val own = Playback("film-1", playing = true, pos = 30.0, rate = 1.0)
    private val live = FakeTogetherLive()

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(scope: kotlinx.coroutines.test.TestScope): CastSenderViewModel {
        scope.installMainDispatcher()
        val watch = WatchStateFixture(listOf(Profile("A", "Anna")), chosen = "A")
        return CastSenderViewModel(live, watch.repository, FakeHouseholdsRepository(), RuntimeEnvironment.getApplication())
    }

    private fun answer(
        answer: Answer,
        note: String = "",
        id: Int = 11,
    ) = CastAnswer(
        answer,
        LiveSession(id, offered("s", Member("tab", "Tablet", remote = true), own, "A", HOME_HOUSEHOLD, 0L)),
        note,
    )

    @Test
    fun onOpensTheRemote() = runTest {
        val vm = viewModel(this)
        live.onOffer = { answer(Answer.ON) }

        vm.playOnTv(own)

        assertEquals(CastAsk.On(11), vm.ask.value)
        assertEquals(listOf("offer Phone film-1 A home"), live.casts)
    }

    @Test
    fun busyAsksThenReplaceIsOn() = runTest {
        val vm = viewModel(this)
        live.onOffer = { answer(Answer.BUSY, note = "Dune") }
        live.onReplace = { answer(Answer.ON) }

        vm.playOnTv(own)
        assertEquals(CastAsk.Busy(11, "Dune"), vm.ask.value)
        vm.replace()

        assertEquals(CastAsk.On(11), vm.ask.value)
        assertEquals(listOf("offer Phone film-1 A home", "replace 11 film-1 A home"), live.casts)
    }

    @Test
    fun busyThenCancelSendsCancel() = runTest {
        val vm = viewModel(this)
        live.onOffer = { answer(Answer.BUSY, note = "Dune") }

        vm.playOnTv(own)
        vm.cancel()

        assertNull(vm.ask.value)
        assertEquals(listOf("offer Phone film-1 A home", "cancel 11"), live.casts)
    }

    @Test
    fun refusedTellsTheReason() = runTest {
        val vm = viewModel(this)
        live.onOffer = { answer(Answer.REFUSED, note = "the TV's library doesn't have it yet") }

        vm.playOnTv(own)

        assertEquals(CastAsk.Told(refusedLine("the TV's library doesn't have it yet")), vm.ask.value)
        vm.dismiss()
        assertNull(vm.ask.value)
    }

    @Test
    fun noAnswerTellsMediagramIsntOpen() = runTest {
        val vm = viewModel(this)
        live.onOffer = { CastAnswer(Answer.NONE, null, "") }

        vm.playOnTv(own)

        assertEquals(CastAsk.Told(CastWords.NO_ANSWER), vm.ask.value)
    }

    @Test
    fun aFailedSendIsUnreachable() = runTest {
        val vm = viewModel(this)
        live.onOffer = { error("offline") }

        vm.playOnTv(own)

        assertEquals(CastAsk.Told(TOGETHER_UNREACHABLE), vm.ask.value)
    }

    @Test
    fun unreachableIsGreyedWithTheReason() = runTest {
        val vm = viewModel(this)
        assertNull(vm.castable.value)

        live.available.value = false

        assertEquals(TOGETHER_UNREACHABLE, vm.castable.value)
    }

    @Test
    fun inASessionIsGreyedLeaveFirstUntilItEnds() = runTest {
        val vm = viewModel(this)

        vm.inSession(true)
        assertEquals(CastWords.LEAVE_FIRST, vm.castable.value)
        vm.inSession(false)

        assertNull(vm.castable.value)
    }

    @Test
    fun leavingWhileBusyWithdrawsTheOffer() = runTest {
        val vm = viewModel(this)
        live.onOffer = { answer(Answer.BUSY, note = "Dune") }
        vm.playOnTv(own)

        ViewModelStore().apply { put("sender", vm) }.clear()

        assertEquals(listOf("offer Phone film-1 A home", "cancel 11"), live.casts)
    }

    @Test
    fun aSecondPressWhileAskingSendsNothing() = runTest {
        val vm = viewModel(this)
        val reply = CompletableDeferred<CastAnswer>()
        live.onOffer = { reply.await() }

        vm.playOnTv(own)
        vm.playOnTv(own.copy(set = "film-2"))
        assertEquals(CastAsk.Asking, vm.ask.value)
        reply.complete(answer(Answer.ON))
        runCurrent()

        assertEquals(listOf("offer Phone film-1 A home"), live.casts)
    }

    @Test
    fun noReplyBeyondTheServersDeadlineIsNoAnswer() = runTest {
        val vm = viewModel(this)
        live.onOffer = { CompletableDeferred<CastAnswer>().await() }

        vm.playOnTv(own)
        advanceTimeBy(CAST_ANSWER_MS + 2_900)
        assertEquals(CastAsk.Asking, vm.ask.value)
        advanceTimeBy(200)

        assertEquals(CastAsk.Told(CastWords.NO_ANSWER), vm.ask.value)
    }
}
