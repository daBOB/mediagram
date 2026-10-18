package player

import data.ArmedCast
import data.SPEED_PREFERENCE
import data.WatchStateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import model.Profile
import org.junit.After
import testing.FakeTogetherLive
import testing.WatchStateFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A TV playing a cast plays as the caster: progress, the daily limit and
 * preferences are the caster's, and the TV's own chosen profile — its
 * places, its Continue, the snapshot it shows — is left exactly as it was.
 */
class PlayerPlayingAsTest {
    private val grownUp = Profile("A", "Anna")
    private val kid = Profile("K", "Kim", kids = true, kidsAge = 6, dailyLimit = 30)
    private val cast = ArmedCast(id = 900, set = "s1", profile = "K", from = "Tablet", playing = true)

    private val handle = FakePlayerHandle().apply { fakeDurationMs = 2_400_000L }

    private fun fixture(seed: suspend WatchStateRepository.() -> Unit = {}) =
        WatchStateFixture(profiles = listOf(grownUp, kid), chosen = "A", seed = seed)

    private fun casting(armed: ArmedCast? = cast) = FakeTogetherLive().apply { armed?.let(::castOnOpen) }

    /** Plays ten seconds, long enough for the save ticker's first write. */
    private fun TestScope.playTenSeconds() {
        handle.fakePositionMs = 10_000L
        handle.emitPlaying(true)
        advanceTimeBy(10_001)
        runCurrent()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun castProgressGoesToTheCaster() = runTest {
        installMainDispatcher()
        val f = fixture()
        val vm = buildViewModel(handle, f.repository, together = casting())

        vm.open("s1")
        assertEquals(kid, vm.playingAs.value)
        playTenSeconds()
        vm.stop()
        advanceUntilIdle()

        assertEquals(listOf("s1" to 10.0), f.core.snapshot("K").progress.map { it.setId to it.at })
        assertTrue(f.core.snapshot("A").progress.isEmpty())
    }

    @Test
    fun castDailyLimitIsTheCasters() = runTest {
        installMainDispatcher()
        val f = fixture().also { it.core.watchedTodayByProfile["K"] = 1_800.0 }
        val catalog = FakeCatalogRepository(mapOf("s2" to fakeMediaSet("s2", durationSecs = 100)))
        val vm = buildViewModel(handle, f.repository, catalogRepository = catalog, together = casting())
        vm.open("s1", listOf("s1", "s2"))
        runCurrent()

        vm.playNext()
        runCurrent()

        // The TV's own grown-up has no limit; only the kid's spent half hour refuses this.
        assertNull(vm.pendingSwitch.value)
        assertEquals("s1", handle.openedSetId)
        vm.stop()
    }

    @Test
    fun castPreferencesAreTheCasters() = runTest {
        installMainDispatcher()
        val preferences = FakePlayerPreferences(
            mapOf(
                ("K" to "set:s1") to mapOf(SPEED_PREFERENCE to "1.5"),
                ("A" to "set:s1") to mapOf(SPEED_PREFERENCE to "1.25"),
            ),
        )
        val vm = buildViewModel(handle, fixture().repository, preferences = preferences, together = casting())

        vm.open("s1")
        runCurrent()
        assertEquals(1.5f, vm.choices.value.speed)
        vm.setSpeed(2f)
        runCurrent()

        assertEquals(listOf("K set:s1 speed=2"), preferences.remembered)
        vm.stop()
    }

    @Test
    fun tvProfileIsUntouchedByACast() = runTest {
        installMainDispatcher()
        val f = fixture {
            setProgress("s1", 600.0, 2400.0)
            setProgress("s3", 300.0, 2400.0)
        }
        val shown = f.repository.snapshot.value
        val places = f.core.snapshot("A").progress
        assertEquals(listOf("s1", "s3"), shown.progress.map { it.setId }.sorted())
        val vm = buildViewModel(handle, f.repository, together = casting())

        vm.open("s1")
        playTenSeconds()
        handle.emitPlaying(false)
        vm.stop()
        advanceUntilIdle()

        assertEquals(listOf(10.0), f.core.snapshot("K").progress.map { it.at })
        assertEquals("A", f.repository.chosenProfileId.value)
        assertEquals(shown, f.repository.snapshot.value)
        assertEquals(places, f.core.snapshot("A").progress)
        assertNull(vm.playingAs.value)
    }

    @Test
    fun anOpenWithoutACastPlaysAsTheChosen() = runTest {
        installMainDispatcher()
        val f = fixture()
        val vm = buildViewModel(handle, f.repository, together = casting(armed = null))

        vm.open("s1")
        assertNull(vm.playingAs.value)
        playTenSeconds()
        vm.stop()
        advanceUntilIdle()

        assertEquals(listOf("s1" to 10.0), f.core.snapshot("A").progress.map { it.setId to it.at })
        assertTrue(f.core.snapshot("K").progress.isEmpty())
    }

    @Test
    fun aCastForAnotherTitleIsNotPlayedAs() = runTest {
        installMainDispatcher()
        val together = casting(cast.copy(set = "s2"))
        val vm = buildViewModel(handle, fixture().repository, together = together)

        vm.open("s1")

        assertNull(vm.playingAs.value)
        assertNull(together.takeCast()) // taken, and dropped: it was never this title's
        vm.stop()
    }

    @Test
    fun theCasterStaysThroughAReopenAndAnUpNextSwitch() = runTest {
        installMainDispatcher()
        val vm = buildViewModel(handle, fixture().repository, together = casting())

        vm.open("s1")
        vm.open("s1") // a rotation's reopen: nothing is armed any more
        assertEquals(kid, vm.playingAs.value)
        vm.open("s2") // the next episode, still the caster's
        assertEquals(kid, vm.playingAs.value)

        vm.stop()
        assertNull(vm.playingAs.value)
    }
}
