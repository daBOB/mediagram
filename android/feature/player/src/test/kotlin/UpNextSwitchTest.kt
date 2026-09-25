package player

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [UpNextController]'s switch itself — never opened here directly (see
 * [UpNextSwitcher]'s own doc): each test drives it exactly the way
 * `PlayerScreen`'s effect and `PlayerViewModel.open` really do — read
 * [UpNextController.pendingSwitch], reopen through
 * [UpNextController.startTitle] for the same id. [buildController] lives
 * in [UpNextControllerTest].
 */
class UpNextSwitchTest {

    /**
     * The real re-entrant path: the countdown finishes inside its own
     * coroutine and asks for a switch; cancelling itself there does no harm
     * (no suspension follows). The gate only starts once the caller — here
     * standing in for `PlayerViewModel.open` — reopens the same id through
     * [UpNextController.startTitle], the way the switch actually lands.
     */
    @Test
    fun endingAsksForASwitchAndTheGateStartsOnceItLands() = runTest {
        val handle = FakePlayerHandle()
        val catalogRepository = FakeCatalogRepository(mapOf("s2" to fakeMediaSet("s2", durationSecs = 100, fsk = "12")))
        val (controller, session) = buildController(this, handle, catalogRepository)
        session.open("s1")
        controller.startTitle("s1", listOf("s1", "s2"))
        runCurrent()

        controller.onEnded()
        assertEquals(UpNextPhase.COUNTING, controller.state.value.phase)

        advanceTimeBy(10_000) // the countdown running out
        runCurrent()

        val switch = controller.pendingSwitch.value
        assertEquals(PendingPlayerSwitch("s2", listOf("s1", "s2")), switch)
        assertFalse(controller.playWhenReadyFor("s2")) // paused, waiting on the gate
        assertFalse(handle.playCalled)

        // What `PlayerViewModel.open` really does once `LibraryPositions` has moved.
        session.open("s2")
        controller.startTitle("s2", requireNotNull(switch).run)
        assertTrue(controller.state.value.awaitingStart)

        // The gate polls every 500ms; a minute buffered ahead satisfies it.
        handle.fakeBufferedPositionMs = 61_000L
        advanceTimeBy(600)
        runCurrent()

        assertTrue(handle.playCalled)
        assertFalse(controller.state.value.awaitingStart)
        controller.stop()
    }

    @Test
    fun startingPlaybackByHandCancelsAPendingGate() = runTest {
        // Set so the gate would satisfy on its very first poll, if it ran one.
        val handle = FakePlayerHandle().apply { fakeBufferedPositionMs = 61_000L }
        val catalogRepository = FakeCatalogRepository(mapOf("s2" to fakeMediaSet("s2", durationSecs = 100)))
        val (controller, session) = buildController(this, handle, catalogRepository)
        session.open("s1")
        controller.startTitle("s1", listOf("s1", "s2"))
        runCurrent()
        controller.onEnded()
        advanceTimeBy(10_000)
        runCurrent()
        val switch = requireNotNull(controller.pendingSwitch.value)
        session.open("s2")
        controller.startTitle("s2", switch.run)
        assertTrue(controller.state.value.awaitingStart)

        // The viewer pressed play before the gate's own first poll ran.
        controller.onPlayingChanged(true)

        assertFalse(controller.state.value.awaitingStart)
        advanceTimeBy(2_000)
        runCurrent()
        assertFalse(handle.playCalled) // cancelled before it ever got to call it
        controller.stop()
    }

    @Test
    fun playNowAsksForAnImmediateSwitchWithNoGate() = runTest {
        val handle = FakePlayerHandle()
        val (controller, session) = buildController(this, handle, FakeCatalogRepository(mapOf("s2" to fakeMediaSet("s2"))))
        session.open("s1")
        controller.startTitle("s1", listOf("s1", "s2"))
        runCurrent()

        controller.playNow()

        assertEquals(PendingPlayerSwitch("s2", listOf("s1", "s2")), controller.pendingSwitch.value)
        assertTrue(controller.playWhenReadyFor("s2")) // "asap" opens normally, not through the gate
        controller.switchAcknowledged()
        assertNull(controller.pendingSwitch.value)
        controller.stop()
    }

    @Test
    fun theRunUpdatesForTheStillOpenTitleOnceTheCatalogArrives() = runTest {
        val (controller, session) = buildController(this, catalogRepository = FakeCatalogRepository(mapOf("s2" to fakeMediaSet("s2"))))
        session.open("s1")
        controller.startTitle("s1", emptyList()) // the catalog was not Ready yet
        runCurrent()
        assertFalse(controller.state.value.hasNext)

        controller.updateRun("s1", listOf("s1", "s2"))
        runCurrent()

        assertTrue(controller.state.value.hasNext)
        assertEquals("s2", controller.state.value.titleLine)
        controller.stop()
    }
}
