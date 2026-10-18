package player.together

import data.ArmedCast
import data.LiveSession
import data.TogetherLive
import io.mockk.every
import kotlinx.coroutines.test.TestScope
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
import kotlin.test.assertTrue

/** The remote that casts to the TV in these tests. */
internal val TABLET = Member("tab-1", "Tablet")

/** The tablet's offer of [EP1], playing two minutes in, for a kid's profile. */
internal val CAST_OFFER = offered("s-cast", TABLET, Playback(EP1, playing = true, pos = 120.0, rate = 1.0), "kid", NOW)

/** [CAST_OFFER] as message 960, armed for the TV's player. */
internal val CAST = ArmedCast(960, EP1, "kid", "Tablet", playing = true)

/** The TV, holding [CAST_OFFER] as message 960, heard before its player opened. */
internal suspend fun TestScope.tv(wrap: (TogetherLive) -> TogetherLive = { it }): TogetherRig {
    val rig = TogetherRig(this, label = "TV", wrap = wrap)
    rig.put(960, CAST_OFFER)
    rig.live.refresh()
    return rig
}

/** The TV opened with [CAST], its accept written. */
internal suspend fun TestScope.casting(): TogetherRig =
    tv().also {
        it.controller.open(EP1, CAST)
        runCurrent()
    }

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
}
