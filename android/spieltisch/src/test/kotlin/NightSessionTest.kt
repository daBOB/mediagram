package com.mediagram.spieltisch

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** What outlives one screen: the stop under way and the NSD registration. */
@OptIn(ExperimentalCoroutinesApi::class)
class NightSessionTest {
    private val core = FakeSpieltischCore()
    private val announcer = RecordingAnnouncer()

    @BeforeTest fun mainOnTheTestThread() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun resetMain() = Dispatchers.resetMain()

    @Test
    fun aStartThatFinishesAfterTheStopLeavesNothingAnnounced() =
        runTest {
            // The stop's own work waits until the test lets the session's scope run.
            val session = NightSession(core, announcer, CoroutineScope(StandardTestDispatcher(testScheduler)), "")
            val gate = CompletableDeferred<Unit>()
            core.startGate = gate
            val model = GameNightViewModel(session)

            model.start()
            model.stop()
            // The host answers before the stop gets to cancel the start: the start announces.
            gate.complete(Unit)
            assertEquals("http://192.168.0.2:8080/", announcer.live)
            testScheduler.advanceUntilIdle()

            assertEquals(null, announcer.live)
            assertEquals(1, core.stops)
        }

    @Test
    fun aNewScreensNightWaitsForTheLastScreensStop() =
        runTest {
            val session = NightSession(core, announcer, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), "")
            val left = GameNightViewModel(session)
            left.start()
            left.state.first { it !is GameNightUi.Starting }
            val stopping = CompletableDeferred<Unit>()
            core.stopGate = stopping

            left.stop()
            val next = GameNightViewModel(session)
            next.start()
            testScheduler.advanceUntilIdle()
            assertEquals(1, core.starts.size, "the next screen's night waits for the last one's stop")

            stopping.complete(Unit)
            assertEquals(GameNightUi.Ready("http://127.0.0.1:8080/tv"), next.state.first { it !is GameNightUi.Starting })
            assertEquals(2, core.starts.size)
            assertEquals("http://192.168.0.2:8080/", announcer.live)
        }
}
