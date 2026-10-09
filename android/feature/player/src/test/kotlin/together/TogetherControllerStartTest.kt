package player.together

import data.TOGETHER_UNREACHABLE
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import model.together.Playback
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Starting, joining and leaving a session from the player: the button, Home's Join, and the player closing. */
class TogetherControllerStartTest {
    @Test
    fun startingSendsThisScreensOwnPlaybackAndTheButtonBecomesLeave() =
        runTest {
            val rig = TogetherRig(this)

            rig.openAndPress()

            val sent = rig.message(900)
            assertEquals(Playback(EP1, playing = true, pos = 10.0, rate = 1.0), Playback(sent.set, sent.playing, sent.pos, sent.rate))
            assertEquals(listOf("Tablet"), sent.members.map { it.label })
            assertEquals(TogetherUi(TogetherButton.LEAVE, enabled = true, label = "Together · Tablet"), rig.controller.ui.value)
        }

    @Test
    fun aSessionAlreadyLiveIsJoinedRatherThanStartedBesideIt() =
        runTest {
            val rig = TogetherRig(this)
            rig.put(950, TV_SESSION)
            rig.controller.open(EP1)
            runCurrent()
            assertEquals(TogetherUi(TogetherButton.JOIN, enabled = true), rig.controller.ui.value)

            rig.controller.onButton()
            runCurrent()

            assertEquals(setOf(950), rig.core.togetherMessages.keys)
            assertEquals(listOf("TV", "Tablet"), rig.message(950).members.map { it.label })
            assertEquals("Together · TV, Tablet", rig.controller.ui.value.label)
        }

    @Test
    fun joiningASessionOnAnotherTitleOpensThatTitleAtItsPlaceAndJoinsAsItOpens() =
        runTest {
            val rig = TogetherRig(this)
            rig.put(950, TV_SESSION.copy(set = EP2, playing = false, pos = 300.0))
            rig.openAndPress()
            assertEquals(listOf(EP2), rig.opened)
            assertTrue(rig.core.togetherEdits.isEmpty())

            val start = rig.controller.open(EP2)
            runCurrent()

            assertEquals(Playback(EP2, playing = false, pos = 300.0, rate = 1.0), start)
            assertEquals(listOf("TV", "Tablet"), rig.message(950).members.map { it.label })
            assertEquals(TogetherButton.LEAVE, rig.controller.ui.value.button)
        }

    @Test
    fun aJoinAskedForOnHomeStartsTheTitleAtTheSessionsPlaceAndJoinsIt() =
        runTest {
            val rig = TogetherRig(this)
            rig.put(950, TV_SESSION.copy(playing = false, pos = 300.0))
            rig.live.refresh()
            rig.live.joinOnOpen(950)

            val start = rig.controller.open(EP1)
            runCurrent()

            assertEquals(Playback(EP1, playing = false, pos = 300.0, rate = 1.0), start)
            assertEquals(listOf("TV", "Tablet"), rig.message(950).members.map { it.label })
        }

    @Test
    fun aTitleOpenedOutsideAnySessionStartsWhereThisProfileLeftOff() =
        runTest {
            assertNull(TogetherRig(this).controller.open(EP1))
        }

    @Test
    fun aSessionOnATitleThisProfileMayNotSeeIsGreyedAndCannotBeJoined() =
        runTest {
            val rig = TogetherRig(this)
            rig.put(950, TV_SESSION.copy(set = HIDDEN))
            rig.controller.open(EP1)
            runCurrent()
            assertEquals(TogetherUi(TogetherButton.JOIN, enabled = false, reason = NOT_HERE), rig.controller.ui.value)

            rig.controller.onButton()
            runCurrent()

            assertEquals(NOT_HERE, rig.controller.ui.value.notice)
            assertTrue(rig.opened.isEmpty())
            assertEquals(listOf(TV), rig.message(950).members)
        }

    @Test
    fun withTelegramOutOfReachTheButtonIsGreyedAndAStartSaysWhy() =
        runTest {
            val rig = TogetherRig(this)
            rig.core.togetherFailure = IllegalStateException("offline")
            rig.controller.open(EP1)
            runCurrent()
            assertEquals(TogetherUi(TogetherButton.START, enabled = false, reason = TOGETHER_UNREACHABLE), rig.controller.ui.value)

            rig.controller.onButton()
            runCurrent()

            assertEquals(TOGETHER_UNREACHABLE, rig.controller.ui.value.notice)
            assertEquals(TogetherButton.START, rig.controller.ui.value.button)
        }

    @Test
    fun theLastScreenToLeaveEndsTheSessionAndDeletesIt() =
        runTest {
            val rig = TogetherRig(this)
            rig.openAndPress()

            rig.controller.onButton()
            runCurrent()

            assertEquals(listOf(true), rig.edits.map { it.ended })
            assertEquals(listOf(900), rig.core.togetherDeletes)
            assertEquals(TogetherUi(TogetherButton.START, enabled = true), rig.controller.ui.value)
        }

    @Test
    fun closingThePlayerTakesOnlyThisScreenOutOfASessionOthersAreIn() =
        runTest {
            val rig = TogetherRig(this).joined()

            rig.controller.close()
            runCurrent()

            assertEquals(listOf(TV), rig.message(950).members)
            assertTrue(rig.core.togetherDeletes.isEmpty())
            assertEquals(TogetherButton.JOIN, rig.controller.ui.value.button)
        }

    @Test
    fun aStartAnsweredAfterThePlayerClosedTakesThisScreenOutAgain() =
        runTest {
            val rig = TogetherRig(this)
            rig.controller.open(EP1)
            runCurrent()
            val answer = CompletableDeferred<Unit>().also { rig.core.togetherSendGate = it }
            rig.controller.onButton()
            runCurrent()
            assertEquals(setOf(900), rig.core.togetherMessages.keys)

            rig.controller.close()
            answer.complete(Unit)
            runCurrent()

            assertEquals(listOf(true), rig.edits.map { it.ended })
            assertEquals(listOf(900), rig.core.togetherDeletes)
            assertEquals(TogetherButton.START, rig.controller.ui.value.button)
        }

    @Test
    fun aStartLeftUnansweredIsGivenUpAndLeftOnceItIsAnswered() =
        runTest {
            val rig = TogetherRig(this)
            rig.controller.open(EP1)
            runCurrent()
            val answer = CompletableDeferred<Unit>().also { rig.core.togetherSendGate = it }
            rig.controller.onButton()
            runCurrent()

            advanceTimeBy(ASK_MS)
            runCurrent()
            assertEquals(COULD_NOT_START, rig.controller.ui.value.notice)
            assertEquals(TogetherButton.START, rig.controller.ui.value.button)

            answer.complete(Unit)
            runCurrent()
            assertEquals(listOf(900), rig.core.togetherDeletes)
        }

    @Test
    fun aStartThatMetAnotherScreensSessionAfterThePlayerClosedLeavesThatOneAlone() =
        runTest {
            val rig = TogetherRig(this)
            rig.controller.open(EP1)
            runCurrent()
            // Started on the TV after this screen last read the channel.
            rig.put(950, TV_SESSION)
            val held = CompletableDeferred<Unit>()
            rig.provider.beforeCore = { held.await() }
            rig.controller.onButton()
            runCurrent()

            rig.controller.close()
            held.complete(Unit)
            runCurrent()

            assertTrue(rig.core.togetherEdits.isEmpty())
            assertEquals(listOf(TV), rig.message(950).members)
        }
}
