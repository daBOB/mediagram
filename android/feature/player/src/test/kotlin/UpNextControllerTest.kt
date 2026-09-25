package player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import model.MediaSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [UpNextController]'s phase ticker and countdown — the switch it drives is
 * [UpNextSwitchTest]'s own file, split apart to keep both under the
 * project's line guideline; [buildController] is shared between them.
 */
class UpNextControllerTest {

    @Test
    fun theStandingButtonAppearsOnceTheNextTitleResolves() = runTest {
        val (controller, session) = buildController(this, catalogRepository = FakeCatalogRepository(mapOf("s2" to fakeMediaSet("s2"))))
        session.open("s1")
        controller.startTitle("s1", listOf("s1", "s2"))
        runCurrent()

        assertTrue(controller.state.value.hasNext)
        assertEquals("s2", controller.state.value.titleLine)
        controller.stop()
    }

    @Test
    fun theCardEntersTheWarnWindowOnceTheTickerCatchesUp() = runTest {
        val handle = FakePlayerHandle().apply { fakePositionMs = 1_000L }
        val openSet = MutableStateFlow<MediaSet?>(fakeMediaSet("s1", durationSecs = 40))
        val (controller, session) = buildController(this, handle, FakeCatalogRepository(mapOf("s2" to fakeMediaSet("s2"))), openSet)
        session.open("s1")
        controller.startTitle("s1", listOf("s1", "s2"))
        controller.onPlayingChanged(true) // the phase ticker only runs while playing
        runCurrent()
        assertEquals(UpNextPhase.HIDDEN, controller.state.value.phase)

        handle.fakePositionMs = 15_000L // the title has played on, into the last 30 seconds
        // Past the boundary, not exactly on it — a delay scheduled for
        // exactly this many milliseconds out is not guaranteed to have run
        // yet at the instant `advanceTimeBy` lands on it; `runCurrent()`
        // drains whatever that left ready.
        advanceTimeBy(1_100)
        runCurrent()

        assertEquals(UpNextPhase.WAITING, controller.state.value.phase)
        controller.stop()
    }

    /**
     * A ticker left running past a pause is exactly the bug this pins: every
     * existing test that opens a title and never calls `stop()` (most of
     * this module's suite) relies on nothing outliving the pause it already
     * triggers before the test ends, the same way [PlayerSession]'s own
     * ticker has always worked.
     */
    @Test
    fun thePhaseTickerStopsOncePlaybackPauses() = runTest {
        val handle = FakePlayerHandle().apply { fakePositionMs = 1_000L }
        val openSet = MutableStateFlow<MediaSet?>(fakeMediaSet("s1", durationSecs = 40))
        val (controller, session) = buildController(this, handle, FakeCatalogRepository(mapOf("s2" to fakeMediaSet("s2"))), openSet)
        session.open("s1")
        controller.startTitle("s1", listOf("s1", "s2"))
        controller.onPlayingChanged(true)
        runCurrent()

        controller.onPlayingChanged(false)
        handle.fakePositionMs = 15_000L // now inside the warn window, but nothing is ticking to notice
        advanceTimeBy(5_000)

        assertEquals(UpNextPhase.HIDDEN, controller.state.value.phase)
    }

    @Test
    fun cancelHidesTheCardButTheStandingButtonStays() = runTest {
        val (controller, session) = buildController(this, catalogRepository = FakeCatalogRepository(mapOf("s2" to fakeMediaSet("s2"))))
        session.open("s1")
        controller.startTitle("s1", listOf("s1", "s2"))
        runCurrent()
        controller.onEnded()
        assertEquals(UpNextPhase.COUNTING, controller.state.value.phase)

        controller.cancel()

        assertEquals(UpNextPhase.HIDDEN, controller.state.value.phase)
        assertTrue(controller.state.value.hasNext)
        controller.stop()
    }

    @Test
    fun aSeekWhilePausedUpdatesTheCardWithoutStartingTheTicker() = runTest {
        val handle = FakePlayerHandle().apply { fakePositionMs = 1_000L }
        val openSet = MutableStateFlow<MediaSet?>(fakeMediaSet("s1", durationSecs = 40))
        val (controller, session) = buildController(this, handle, FakeCatalogRepository(mapOf("s2" to fakeMediaSet("s2"))), openSet)
        session.open("s1")
        controller.startTitle("s1", listOf("s1", "s2"))
        runCurrent()
        assertEquals(UpNextPhase.HIDDEN, controller.state.value.phase) // outside the warn window

        handle.fakePositionMs = 15_000L // a seek into the last 30 seconds, while paused
        controller.onSeeked()

        assertEquals(UpNextPhase.WAITING, controller.state.value.phase)
        controller.stop()
    }
}

internal fun buildController(
    scope: TestScope,
    handle: FakePlayerHandle = FakePlayerHandle(),
    catalogRepository: FakeCatalogRepository = FakeCatalogRepository(),
    openSet: MutableStateFlow<MediaSet?> = MutableStateFlow(null),
): Pair<UpNextController, PlayerSession> {
    val session = PlayerSession(scope, handle, ProgressRecorder(FakeWatchStateRepository()))
    val controller = UpNextController(scope, handle, session, catalogRepository, openSet)
    return controller to session
}
