package player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every way the player goes away leaves a watch-together session, even once
 * the ViewModel itself is gone; and out of one, the player stops listening
 * for sessions while its screen is not there to show them.
 */
class PlayerTogetherLeavingTest {
    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun closingThePlayerLeavesTheSession() =
        runTest {
            installMainDispatcher()
            val rig = PlayerTogetherRig(this).apply { inASession() }

            rig.vm.stop()
            runCurrent()

            assertEquals(listOf(true), rig.edits.map { it.ended })
            assertEquals(listOf(900), rig.core.togetherDeletes)
        }

    @Test
    fun theViewModelGoingAwayStillLeavesTheSession() =
        runTest {
            installMainDispatcher()
            val rig = PlayerTogetherRig(this).apply { inASession() }

            rig.clear()
            runCurrent()

            assertEquals(listOf(true), rig.edits.map { it.ended })
            assertEquals(listOf(900), rig.core.togetherDeletes)
        }

    @Test
    fun dismissingPictureInPictureLeavesWithoutPausingTheOthers() =
        runTest {
            installMainDispatcher()
            val rig = PlayerTogetherRig(this)
            rig.put(950, tvSession())
            rig.inASession()

            rig.vm.pauseForPipDismissal()
            runCurrent()

            assertTrue(rig.handle.pauseCalled)
            assertEquals(listOf(TV_SCREEN), rig.message(950).members)
            assertTrue(rig.message(950).playing)
        }

    @Test
    fun dismissingPictureInPictureOutsideASessionStopsListening() =
        runTest {
            installMainDispatcher()
            val rig = PlayerTogetherRig(this)
            rig.vm.open("film-1")
            runCurrent()
            assertEquals(1, rig.events.subscriptionCount.value)

            rig.vm.pauseForPipDismissal()
            runCurrent()

            assertEquals(0, rig.events.subscriptionCount.value)
        }

    @Test
    fun theScreenStoppingOutOfASessionStopsListeningUntilItStartsAgain() =
        runTest {
            installMainDispatcher()
            val rig = PlayerTogetherRig(this)
            rig.vm.open("film-1")
            runCurrent()

            rig.vm.screenShown(false)
            runCurrent()
            assertEquals(0, rig.events.subscriptionCount.value)

            rig.vm.screenShown(true)
            runCurrent()
            assertEquals(1, rig.events.subscriptionCount.value)
        }
}
