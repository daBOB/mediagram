package player.together

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A start answered after the player closed, or after it stopped waiting, takes the screen out again rather than in. */
class TogetherControllerLateAnswerTest {
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
