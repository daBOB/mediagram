package player

import data.WatchStateRepository
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import model.DailyLimitWords
import model.Profile
import testing.WatchStateFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The daily limit at the player: the gate's answer, the one warning, and the refusal of the next title. */
class DailyLimitTest {
    private val kid = Profile("k1", "Kid", kids = true, kidsAge = 6, dailyLimit = 30)

    private fun fixture(profile: Profile, watchedSeconds: Double = 0.0) =
        WatchStateFixture(listOf(profile)).also { it.core.watchedTodayByProfile[profile.id] = watchedSeconds }

    @Test
    fun aKidOpensWhileTimeIsLeftAndIsRefusedOnceItIsUsedUp() = runTest {
        assertTrue(DailyLimitGate(fixture(kid, 1_000.0).repository).mayOpen()) // ok
        assertTrue(DailyLimitGate(fixture(kid, 1_600.0).repository).mayOpen()) // warn still opens
        assertFalse(DailyLimitGate(fixture(kid, 1_800.0).repository).mayOpen())
    }

    @Test
    fun everyoneElseIsNeverAskedAndNeverRefused() = runTest {
        for (profile in listOf(Profile("p1", "Grown-up"), kid.copy(dailyLimit = null))) {
            val f = fixture(profile, 99_999.0)
            val gate = DailyLimitGate(f.repository)
            assertFalse(gate.applies())
            assertTrue(gate.mayOpen())
            assertTrue(f.core.watchedTodayAsked.isEmpty())
        }
    }

    @Test
    fun aCoreThatCannotAnswerLetsTheTitleOpen() = runTest {
        val real = fixture(kid, 99_999.0).repository
        val broken = object : WatchStateRepository by real {
            override suspend fun watchedToday(profileId: String): Double = error("core down")
        }
        assertTrue(DailyLimitGate(broken).mayOpen())
    }

    private fun warning(scope: TestScope, f: WatchStateFixture, told: MutableList<String>) =
        DailyLimitWarning(scope, DailyLimitGate(f.repository)) { told += it }

    @Test
    fun theWarningComesAtOpenWhenAlreadyNearTheEndAndOnlyOnce() = runTest {
        val told = mutableListOf<String>()
        val f = fixture(kid, 1_600.0)
        val warning = warning(this, f, told)

        warning.opened(); runCurrent()
        warning.tick(); runCurrent()

        assertEquals(listOf(DailyLimitWords.WARNING), told)
    }

    @Test
    fun theWarningComesOnTheTickThatCrossesIntoTheLastFiveMinutes() = runTest {
        val told = mutableListOf<String>()
        val f = fixture(kid, 1_000.0)
        val warning = warning(this, f, told)

        warning.opened(); runCurrent()
        assertTrue(told.isEmpty())
        f.core.watchedTodayByProfile["k1"] = 1_510.0
        warning.tick(); runCurrent()
        warning.tick(); runCurrent()

        assertEquals(listOf(DailyLimitWords.WARNING), told)
    }

    @Test
    fun aNewTitleIsWarnedAgain() = runTest {
        val told = mutableListOf<String>()
        val warning = warning(this, fixture(kid, 1_600.0), told)

        warning.opened(); runCurrent()
        warning.opened(); runCurrent()

        assertEquals(2, told.size)
    }

    @Test
    fun noWarningForSomeoneWithoutALimitOrWithPlentyLeft() = runTest {
        val told = mutableListOf<String>()
        warning(this, fixture(Profile("p1", "Grown-up"), 99_999.0), told).apply { opened(); tick() }
        warning(this, fixture(kid, 10.0), told).apply { opened(); tick() }
        runCurrent()
        assertTrue(told.isEmpty())
    }

    @Test
    fun theSaveTickerCallsTheWarningEveryTenSecondsWhilePlaying() = runTest {
        var ticks = 0
        val handle = FakePlayerHandle().apply { fakePositionMs = 5_000L; fakeDurationMs = 100_000L }
        val session = PlayerSession(this, handle, ProgressRecorder(WatchStateFixture().repository), onTick = { ticks++ })
        session.open("s1")
        session.onPlayingChanged(true)
        advanceTimeBy(25_000)
        runCurrent()
        assertEquals(2, ticks)
        session.stopTicking()
    }

    /** A gate with a fixed answer, counting how often it is asked. */
    private class FixedGate(var applies: Boolean, var open: Boolean) : PlayGate {
        var asked = 0
        override fun applies() = applies
        override suspend fun mayOpen(): Boolean { asked++; return open }
    }

    private fun TestScope.refusing(gate: PlayGate, refused: () -> Unit, handle: FakePlayerHandle = FakePlayerHandle()): Pair<UpNextController, PlayerSession> {
        val session = PlayerSession(this, handle, ProgressRecorder(WatchStateFixture().repository))
        val catalog = FakeCatalogRepository(mapOf("s2" to fakeMediaSet("s2", durationSecs = 100)))
        return UpNextController(this, handle, session, catalog, kotlinx.coroutines.flow.MutableStateFlow(null), gate, refused) to session
    }

    @Test
    fun nextAtZeroLeavesTheFinishedTitleAloneAndSaysSo() = runTest {
        var refused = 0
        val handle = FakePlayerHandle()
        val (controller, session) = refusing(FixedGate(applies = true, open = false), { refused++ }, handle)
        session.open("s1")
        controller.startTitle("s1", listOf("s1", "s2"))
        runCurrent()

        controller.playNow(); runCurrent()
        controller.playFromRun("s2"); runCurrent()
        controller.requestTitle("s2"); runCurrent()

        assertNull(controller.pendingSwitch.value)
        assertEquals(3, refused)
        assertFalse(handle.stopCalled)
        assertNull(handle.openedSetId)
        controller.stop()
    }

    @Test
    fun theCountdownAtZeroIsRefusedOnceAndDoesNotStartOver() = runTest {
        var refused = 0
        val (controller, session) = refusing(FixedGate(applies = true, open = false), { refused++ })
        session.open("s1")
        controller.startTitle("s1", listOf("s1", "s2"))
        runCurrent()
        controller.onEnded()
        assertEquals(UpNextPhase.COUNTING, controller.state.value.phase)

        advanceTimeBy(10_000); runCurrent()
        advanceTimeBy(30_000); runCurrent()

        assertNull(controller.pendingSwitch.value)
        assertEquals(1, refused)
        assertTrue(controller.state.value.phase != UpNextPhase.COUNTING)
        controller.stop()
    }

    @Test
    fun nextSwitchesWhenTimeIsLeftAndWithoutAskingAnyoneElse() = runTest {
        val limited = FixedGate(applies = true, open = true)
        val (controller, session) = refusing(limited, { error("not refused") })
        session.open("s1")
        controller.startTitle("s1", listOf("s1", "s2"))
        runCurrent()
        controller.playNow(); runCurrent()
        assertEquals(PendingPlayerSwitch("s2", listOf("s1", "s2")), controller.pendingSwitch.value)
        assertEquals(1, limited.asked)

        val free = FixedGate(applies = false, open = false)
        val (plain, plainSession) = refusing(free, { error("not refused") })
        plainSession.open("s1")
        plain.startTitle("s1", listOf("s1", "s2"))
        runCurrent()
        plain.playNow() // no runCurrent: the switch is immediate for everyone without a limit
        assertEquals("s2", plain.pendingSwitch.value?.setId)
        assertEquals(0, free.asked)
        controller.stop(); plain.stop()
    }
}
