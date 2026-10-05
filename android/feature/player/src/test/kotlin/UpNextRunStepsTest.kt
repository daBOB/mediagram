package player

import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The run as the card's ⏮ and ⏭ and the episode sidebar walk it: what the
 * up-next state says about either end, and a title picked by hand going
 * through the one switch [UpNextController] already makes for Next.
 */
class UpNextRunStepsTest {

    @Test
    fun previousInQueueIsNextInQueuesMirror() {
        assertEquals("a", previousInQueue(listOf("a", "b", "c"), "b"))
        assertNull(previousInQueue(listOf("a", "b"), "a"))
        assertNull(previousInQueue(listOf("a", "b"), "z"))
        assertNull(previousInQueue(emptyList(), "a"))
    }

    @Test
    fun theFirstTitleOfARunHasANextButNoPrevious() = runTest {
        val (controller, session) = buildController(this)
        session.open("e1")
        controller.startTitle("e1", listOf("e1", "e2", "e3"))
        runCurrent()

        val state = controller.state.value
        assertTrue(state.inRun)
        assertFalse(state.hasPrevious)
        assertTrue(state.hasNext)
        controller.stop()
    }

    @Test
    fun theLastTitleHasAPreviousButNoNext() = runTest {
        val (controller, session) = buildController(this)
        session.open("e3")
        controller.startTitle("e3", listOf("e1", "e2", "e3"))
        runCurrent()

        assertTrue(controller.state.value.hasPrevious)
        assertFalse(controller.state.value.hasNext)
        controller.stop()
    }

    @Test
    fun aFilmIsInNoRunAtAll() = runTest {
        val (controller, session) = buildController(this)
        session.open("film")
        controller.startTitle("film", emptyList())
        runCurrent()

        val state = controller.state.value
        assertFalse(state.inRun)
        assertFalse(state.hasPrevious)
        assertFalse(state.hasNext)
    }

    @Test
    fun aTitlePickedFromTheRunAsksForTheSwitchNextTakes() = runTest {
        val (controller, session) = buildController(this)
        session.open("e2")
        controller.startTitle("e2", listOf("e1", "e2", "e3"))
        runCurrent()

        controller.playFromRun("e1")

        assertEquals(PendingPlayerSwitch("e1", listOf("e1", "e2", "e3")), controller.pendingSwitch.value)
        controller.stop()
    }

    @Test
    fun theOpenTitleOrOneOutsideTheRunIsNoSwitch() = runTest {
        val (controller, session) = buildController(this)
        session.open("e2")
        controller.startTitle("e2", listOf("e1", "e2"))
        runCurrent()

        controller.playFromRun("e2")
        controller.playFromRun("elsewhere")

        assertNull(controller.pendingSwitch.value)
        controller.stop()
    }

    /** The run is ids, and ⏮/⏭ walk ids: one the catalogue has never heard of is still a step. */
    @Test
    fun aRunHoldingAnIdTheCatalogueDoesNotKnowStillSteps() = runTest {
        val (controller, session) = buildController(this) // the fake catalogue knows none of these
        session.open("e1")
        controller.startTitle("e1", listOf("ghost", "e1", "e2"))
        runCurrent()

        assertTrue(controller.state.value.hasPrevious)
        controller.playFromRun("ghost")

        assertEquals("ghost", controller.pendingSwitch.value?.setId)
        controller.stop()
    }
}
