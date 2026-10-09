package player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import model.Profile
import model.together.Member
import model.together.PlaybackChange
import model.together.Session
import org.junit.After
import player.together.NOT_HERE
import player.together.SEEK_SETTLE_MS
import player.together.TogetherButton
import player.together.TogetherUi
import testing.WatchStateFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Watch together through [PlayerViewModel]: every way the viewer moves the
 * player reaches the session once, and every way the player goes away
 * leaves it, even once the ViewModel itself is gone.
 */
class PlayerTogetherWiringTest {
    private val tv = Member("tv-1", "TV")

    private fun tvSession(
        set: String = "film-1",
        playing: Boolean = true,
        pos: Double = 10.0,
    ) = Session("s-tv", set, playing, pos, 1.0, System.currentTimeMillis(), tv.screen, listOf(tv), ended = false)

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
            assertEquals(listOf(tv), rig.message(950).members)
            assertTrue(rig.message(950).playing)
        }

    @Test
    fun aSpeedPickedByHandReachesTheSession() =
        runTest {
            installMainDispatcher()
            val rig = PlayerTogetherRig(this).apply { inASession() }

            rig.vm.setSpeed(1.5f)
            runCurrent()

            assertEquals(1.5, rig.message(900).rate)
            rig.clear()
        }

    @Test
    fun restartingReachesTheSessionAsASeekToTheTop() =
        runTest {
            installMainDispatcher()
            val rig = PlayerTogetherRig(this).apply { inASession() }

            rig.vm.restart()
            advanceTimeBy(SEEK_SETTLE_MS)
            runCurrent()

            assertTrue(rig.message(900).pos < 1.0)
            rig.clear()
        }

    @Test
    fun aSeekThroughThePlayerTheScreenHoldsReachesTheSession() =
        runTest {
            installMainDispatcher()
            val rig = PlayerTogetherRig(this).apply { inASession() }

            rig.vm.player.value!!.seekTo(61_000L)
            advanceTimeBy(SEEK_SETTLE_MS)
            runCurrent()

            assertTrue(rig.message(900).pos in 61.0..62.0)
            rig.clear()
        }

    @Test
    fun aPauseFromTheMediaSessionReachesTheSession() =
        runTest {
            installMainDispatcher()
            val rig = PlayerTogetherRig(this).apply { inASession() }

            rig.relay.report(PlaybackChange(playing = false))
            runCurrent()

            assertEquals(false, rig.message(900).playing)
            rig.clear()
        }

    @Test
    fun theEndOfAFilmEndsTheSessionForEveryone() =
        runTest {
            installMainDispatcher()
            val rig = PlayerTogetherRig(this)
            rig.put(950, tvSession())
            rig.inASession()

            rig.handle.emitEnded()
            runCurrent()

            assertEquals(true, rig.edits.last().ended)
            assertEquals(listOf(950), rig.core.togetherDeletes)
        }

    @Test
    fun theEndOfAnEpisodeWithAnotherNextLeavesTheMoveToUpNext() =
        runTest {
            installMainDispatcher()
            val rig = PlayerTogetherRig(this).apply { inASession("e1", listOf("e1", "e2")) }

            rig.handle.emitEnded()
            runCurrent()

            assertTrue(rig.core.togetherDeletes.isEmpty())
            assertEquals(TogetherButton.LEAVE, rig.vm.together.value.button)
            rig.clear()
        }

    @Test
    fun anotherScreenMovingOnIsAskedOfTheScreenAsNextIs() =
        runTest {
            installMainDispatcher()
            val run = listOf("e1", "e2")
            val rig = PlayerTogetherRig(this)
            rig.put(950, tvSession(set = "e1"))
            rig.inASession("e1", run)

            rig.push(950, rig.message(950).copy(set = "e2", pos = 0.0, at = System.currentTimeMillis(), by = tv.screen))
            runCurrent()

            assertEquals(PendingPlayerSwitch("e2", run), rig.vm.pendingSwitch.value)
            rig.clear()
        }

    @Test
    fun joiningOnOpenStartsAtTheSessionsPlaceNotWhereThisProfileLeftOff() =
        runTest {
            installMainDispatcher()
            val watch = WatchStateFixture(seed = { setProgress("film-1", 1_200.0, 2_400.0) })
            val rig = PlayerTogetherRig(this, watch)
            rig.put(950, tvSession(playing = false, pos = 300.0))
            rig.live.refresh()
            rig.live.joinOnOpen(950)

            rig.vm.open("film-1")
            runCurrent()

            assertEquals(300_000L, rig.handle.openedStartAtMs)
            assertEquals(false, rig.handle.openedPlayWhenReady)
            assertEquals(TogetherButton.LEAVE, rig.vm.together.value.button)
            rig.clear()
        }

    @Test
    fun aKidsProfileIsNotOfferedASessionOnATitleOverItsLimit() =
        runTest {
            installMainDispatcher()
            val kid = WatchStateFixture(listOf(Profile("k1", "Mia", kids = true, kidsAge = 6)))
            val catalog = FakeCatalogRepository(mapOf("film-16" to fakeMediaSet("film-16", fsk = "16"), "e1" to fakeMediaSet("e1", fsk = "0")))
            val rig = PlayerTogetherRig(this, kid, catalog)
            rig.put(950, tvSession(set = "film-16", playing = false))

            rig.vm.open("e1")
            runCurrent()

            assertEquals(TogetherUi(TogetherButton.JOIN, enabled = false, reason = NOT_HERE), rig.vm.together.value)
        }
}
