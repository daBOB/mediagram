package player

import androidx.media3.common.Player
import data.ArmedCast
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import model.Profile
import model.together.Ending
import model.together.Member
import model.together.Playback
import model.together.castFrom
import model.together.endingOf
import model.together.offered
import org.junit.After
import testing.WatchStateFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The TV's player taking a cast: it opens where the offer is, not where the
 * TV's own profile left off; it judges titles by the caster, never by the
 * TV's chosen profile; it takes the cast once; and a kid caster out of time
 * ends it.
 */
class PlayerCastTvTest {
    private val tablet = Member("tab-1", "Tablet")
    private val anna = Profile("A", "Anna")
    private val kim = Profile("K", "Kim", kids = true, kidsAge = 6, dailyLimit = 30)
    private val episodes = listOf("e1", "e2")

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** The TV has heard the tablet's offer of [set] for [profile] as message 960, and armed its player with it. */
    private suspend fun TestScope.armed(
        watch: WatchStateFixture,
        catalog: FakeCatalogRepository = FakeCatalogRepository(listOf("film-1", "e1", "e2").associateWith { fakeMediaSet(it) }),
        set: String = "film-1",
        profile: String = "K",
        playing: Boolean = false,
    ): PlayerTogetherRig {
        val rig = PlayerTogetherRig(this, watch, catalog)
        rig.put(960, offered("s-cast", tablet, Playback(set, playing, pos = 300.0, rate = 1.0), profile, "home", System.currentTimeMillis()))
        rig.live.refresh()
        rig.live.castOnOpen(ArmedCast(960, set, profile, "Tablet", playing))
        return rig
    }

    @Test
    fun aCastOpensWhereTheOfferIsNotWhereTheTvsProfileLeftOff() =
        runTest {
            installMainDispatcher()
            val rig = armed(WatchStateFixture(listOf(anna, kim), chosen = "A", seed = { setProgress("film-1", 1_200.0, 2_400.0) }))

            rig.vm.open("film-1")
            runCurrent()

            assertEquals(300_000L, rig.handle.openedStartAtMs)
            assertEquals(false, rig.handle.openedPlayWhenReady)
            assertEquals(rig.vm.togetherController.screen, rig.message(960).cast?.tv)
            rig.clear()
        }

    @Test
    fun aPlayingCastStartsAtTheFirstFrameThePlayerRenders() =
        runTest {
            installMainDispatcher()
            val rig = armed(WatchStateFixture(listOf(anna, kim), chosen = "A"), playing = true)
            rig.vm.open("film-1")
            runCurrent()
            assertEquals(false, rig.handle.openedPlayWhenReady)
            assertFalse(rig.message(960).playing)

            val listeners = mutableListOf<Player.Listener>()
            verify { rig.player.addListener(capture(listeners)) }
            listeners.forEach { it.onRenderedFirstFrame() }
            runCurrent()

            assertTrue(rig.message(960).playing)
            rig.clear()
        }

    @Test
    fun aKidTvProfileDoesNotTurnAwayAnAdultCastersNextEpisode() =
        runTest {
            installMainDispatcher()
            val mia = Profile("k1", "Mia", kids = true, kidsAge = 6)
            val catalog = FakeCatalogRepository(listOf("e1", "e2").associateWith { fakeMediaSet(it, fsk = "16") })
            val run = listOf("e1", "e2")
            val rig = armed(WatchStateFixture(listOf(mia, anna), chosen = "k1"), catalog, set = "e1", profile = "A")
            rig.vm.open("e1", run)
            runCurrent()

            rig.push(960, rig.message(960).copy(set = "e2", pos = 0.0, at = System.currentTimeMillis(), by = tablet.screen))
            runCurrent()

            assertEquals(PendingPlayerSwitch("e2", run), rig.vm.pendingSwitch.value)
            assertEquals(castFrom("Tablet"), rig.vm.together.value.label)
            rig.clear()
        }

    @Test
    fun aKidsCastOverTheProfilePickerFollowsNextToAMarkedTitle() =
        runTest {
            installMainDispatcher()
            // Nobody chosen on the TV: no snapshot of its own, but the Kids marks are the household's.
            val watch = WatchStateFixture(listOf(anna, kim), chosen = null)
            episodes.forEach { watch.core.setKids(it, 6u) }
            val rig = armed(watch, set = "e1")
            rig.vm.open("e1", episodes)
            runCurrent()

            rig.push(960, rig.message(960).copy(set = "e2", pos = 0.0, at = System.currentTimeMillis(), by = tablet.screen))
            runCurrent()

            assertEquals(PendingPlayerSwitch("e2", episodes), rig.vm.pendingSwitch.value)
            assertFalse(rig.vm.castOver.value)
            rig.clear()
        }

    @Test
    fun anUpNextSwitchKeepsTheCastTakenOnce() =
        runTest {
            installMainDispatcher()
            val rig = armed(WatchStateFixture(listOf(anna, kim), chosen = "A"), set = "e1")
            rig.vm.open("e1", listOf("e1", "e2"))
            runCurrent()

            rig.vm.open("e2", listOf("e1", "e2"))
            runCurrent()

            assertFalse(rig.vm.castOver.value)
            assertEquals("e2", rig.message(960).set)
            assertEquals(rig.vm.togetherController.screen, rig.message(960).cast?.tv)
            rig.clear()
        }

    @Test
    fun anUpNextTheKidCastersLimitRefusesEndsTheCastAsFinished() =
        runTest {
            installMainDispatcher()
            val watch = WatchStateFixture(listOf(anna, kim), chosen = "A").also { it.core.watchedTodayByProfile["K"] = 1_800.0 }
            val rig = armed(watch, set = "e1")
            rig.vm.open("e1", listOf("e1", "e2"))
            runCurrent()

            rig.vm.playNext()
            runCurrent()

            val last = rig.edits.last()
            assertTrue(last.ended)
            assertEquals(Ending.FINISHED, endingOf(last, tablet.screen))
            assertTrue(rig.vm.castOver.value)
            rig.clear()
        }
}
