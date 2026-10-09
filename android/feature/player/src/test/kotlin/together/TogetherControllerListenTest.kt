package player.together

import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Listening for sessions holds the core's push stream open, so it lasts only
 * as long as it is needed: while the player screen is started, or, in a
 * session, for as long as the screen follows it, in the background too.
 */
class TogetherControllerListenTest {
    @Test
    fun outOfASessionTheStreamClosesWhileTheScreenIsStopped() =
        runTest {
            val rig = TogetherRig(this)
            rig.controller.open(EP1)
            runCurrent()
            assertEquals(1, rig.events.subscriptionCount.value)

            rig.controller.screenShown(false)
            runCurrent()

            assertEquals(0, rig.events.subscriptionCount.value)
        }

    @Test
    fun inASessionTheStreamStaysOpenAndFollowedWhileTheScreenIsStopped() =
        runTest {
            val rig = TogetherRig(this).joined()

            rig.controller.screenShown(false)
            runCurrent()
            rig.push(950, rig.message(950).copy(playing = false, at = rig.now(), by = TV.screen))

            assertEquals(1, rig.events.subscriptionCount.value)
            assertTrue(rig.handle.pauseCalled)
        }

    @Test
    fun theScreenStartedAgainListensAgainAndReadsWhatIsLive() =
        runTest {
            val rig = TogetherRig(this)
            rig.controller.open(EP1)
            runCurrent()
            rig.controller.screenShown(false)
            runCurrent()
            rig.put(950, TV_SESSION)

            rig.controller.screenShown(true)
            runCurrent()

            assertEquals(1, rig.events.subscriptionCount.value)
            assertEquals(TogetherButton.JOIN, rig.controller.ui.value.button)
        }

    @Test
    fun aSessionEndingWhileTheScreenIsStoppedLetsTheStreamGo() =
        runTest {
            val rig = TogetherRig(this).joined()
            rig.controller.screenShown(false)
            runCurrent()

            rig.push(950, rig.message(950).copy(ended = true, by = TV.screen))

            assertEquals(0, rig.events.subscriptionCount.value)
        }

    @Test
    fun aJoinAnsweredWhileTheScreenIsStoppedListensFromThen() =
        runTest {
            val rig = TogetherRig(this)
            rig.put(950, TV_SESSION)
            rig.controller.open(EP1)
            runCurrent()
            rig.controller.onButton()
            rig.controller.screenShown(false)

            runCurrent()

            assertEquals(TogetherButton.LEAVE, rig.controller.ui.value.button)
            assertEquals(1, rig.events.subscriptionCount.value)
        }
}
