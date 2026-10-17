package player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import model.DailyLimitWords
import model.Profile
import org.junit.After
import testing.FakeTogetherLive
import testing.WatchStateFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryPlayGateTest {
    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun aRefusalSaysTheOutWordsForTenSecondsAndAnAllowedTitleSaysNothing() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val kid = Profile("k1", "Kid", kids = true, kidsAge = 6, dailyLimit = 30)
        val f = WatchStateFixture(listOf(kid))
        val together = FakeTogetherLive()
        val gate = LibraryPlayGate(f.repository, together)

        f.core.watchedTodayByProfile["k1"] = 600.0
        assertTrue(gate.mayOpen())
        assertNull(gate.notice.value)

        together.joinOnOpen(7)
        f.core.watchedTodayByProfile["k1"] = 1_800.0
        assertFalse(gate.mayOpen())
        assertNull(together.takeJoin())
        assertEquals(DailyLimitWords.OUT, gate.notice.value)
        advanceTimeBy(9_000); runCurrent()
        assertEquals(DailyLimitWords.OUT, gate.notice.value)
        advanceTimeBy(1_500); runCurrent()
        assertNull(gate.notice.value)
    }
}
