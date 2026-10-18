package player.together

import data.ArmedCast
import data.LiveSession
import data.TogetherLive
import io.mockk.every
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import model.together.CastState
import model.together.Ending
import model.together.Member
import model.together.Playback
import model.together.accepted
import model.together.castFrom
import model.together.endingOf
import model.together.finished
import model.together.offered
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The TV in a cast: it takes the offer as its player opens, plays it as the session's TV, and says when the cast is over. Its track list is `TogetherCastTvTracksTest`'s. */
class TogetherCastTvTest {
    @Test
    fun acceptOnOpenJoinsAsTheTv() =
        runTest {
            val rig = tv()

            val start = rig.controller.open(EP1, CAST)
            runCurrent()

            assertEquals(Playback(EP1, playing = false, pos = 120.0, rate = 1.0), start)
            val sent = rig.edits.single()
            assertEquals(listOf(TABLET.copy(remote = true), Member(rig.controller.screen, "TV", remote = false)), sent.members)
            assertEquals(rig.controller.screen, sent.cast?.tv)
            assertEquals(CastState.ON, sent.cast?.state)
            assertFalse(sent.playing)
        }

    @Test
    fun playingOfferStartsAtFirstFrame() =
        runTest {
            val rig = casting()
            every { rig.player.playWhenReady } returns false // opened paused, as the offer asks

            rig.controller.firstFrame()
            runCurrent()
            rig.controller.firstFrame()
            runCurrent()

            assertEquals(listOf(false, true), rig.edits.map { it.playing })
            assertTrue(rig.handle.playCalled)
        }

    @Test
    fun stopBeforeFirstFrameClosesWithoutPlaying() =
        runTest {
            val rig = casting()

            rig.push(960, finished(rig.message(960), TABLET.screen))
            rig.controller.firstFrame()
            runCurrent()

            assertEquals(1, rig.edits.size)
            assertTrue(rig.controller.castOver.value)
        }

    @Test
    fun backOnTheTvEndsTheCast() =
        runTest {
            val rig = casting()

            rig.controller.leave()
            runCurrent()

            val last = rig.edits.last()
            assertTrue(last.ended, "the remote alone does not keep it")
            assertEquals(Ending.STOPPED, endingOf(last, TABLET.screen))
        }

    @Test
    fun anotherTvTookItClosesThePlayer() =
        runTest {
            val rig = tv()
            rig.put(960, accepted(CAST_OFFER, Member("tv-2", "TV"), NOW)!!)

            rig.controller.open(EP1, CAST)
            runCurrent()

            assertTrue(rig.controller.castOver.value)
            assertEquals(emptyList(), rig.edits)
        }

    @Test
    fun labelReadsCastFromTablet() =
        runTest {
            assertEquals(castFrom("Tablet"), casting().controller.ui.value.label)
        }

    @Test
    fun aKidOutOfTimeEndsTheCastAtTheTitlesEnd() =
        runTest {
            val rig = casting()
            rig.controller.onTitleEnded(hasNext = true)
            runCurrent()
            assertEquals(1, rig.edits.size, "with an episode next, the end is Up next's")

            rig.controller.outOfTime()
            runCurrent()

            val last = rig.edits.last()
            assertTrue(last.ended)
            assertEquals(rig.controller.screen, last.by)
            assertEquals(Ending.FINISHED, endingOf(last, TABLET.screen))
            assertTrue(rig.controller.castOver.value)
        }

    @Test
    fun aCastReplacingTheOneThisTvPlaysIsTakenAndStaysOpen() =
        runTest {
            val rig = casting()
            val next = offered("s-next", TABLET, Playback(EP2, playing = false, pos = 30.0, rate = 1.0), "kid", NOW)
            rig.put(961, next)
            rig.live.refresh()

            val start = rig.controller.open(EP2, ArmedCast(961, EP2, "kid", "Tablet", playing = false))
            runCurrent()

            assertEquals(Playback(EP2, playing = false, pos = 30.0, rate = 1.0), start)
            assertTrue(rig.edits.any { it.session == CAST_OFFER.session && it.ended }, "the cast it played is left")
            assertEquals(rig.controller.screen, rig.message(961).cast?.tv)
            assertFalse(rig.controller.castOver.value)
        }

    @Test
    fun anUpNextSwitchDoesNotTakeTheCastAgain() =
        runTest {
            var accepts = 0
            val rig =
                tv { live ->
                    object : TogetherLive by live {
                        override suspend fun accept(
                            id: Int,
                            me: Member,
                        ): LiveSession? = live.accept(id, me).also { accepts++ }
                    }
                }
            rig.controller.open(EP1, CAST)
            runCurrent()

            rig.controller.open(EP2)
            runCurrent()

            assertEquals(1, accepts)
            assertEquals(EP2, rig.message(960).set)
            assertFalse(rig.controller.castOver.value)
        }

    @Test
    fun aTvThatLostTheCastToAnotherClosesItsPlayer() =
        runTest {
            val rig = casting()
            rig.push(960, accepted(CAST_OFFER, Member("tv-2", "TV"), NOW + 500)!!)

            assertTrue(rig.controller.castOver.value)
            assertNull(rig.controller.id)
            assertEquals(1, rig.edits.size, "it was never this TV's: nothing to leave")
        }

    @Test
    fun aCastStoppedBeforeItsFirstFrameLeavesNothingToPlayInALaterSession() =
        runTest {
            val rig = casting()
            rig.push(960, finished(rig.message(960), TABLET.screen))
            rig.put(950, TV_SESSION.copy(playing = false))
            rig.live.refresh()
            rig.controller.onButton()
            runCurrent()
            val joined = rig.edits.size

            rig.controller.firstFrame()
            runCurrent()

            assertEquals(joined, rig.edits.size)
            assertFalse(rig.message(950).playing)
        }

    @Test
    fun aCastOpenTakesAJoinArmedForIt() =
        runTest {
            val rig = tv()
            rig.live.joinOnOpen(950)
            rig.controller.open(EP1, CAST)

            assertNull(rig.live.takeJoin(), "never left for a later open")
        }
}
