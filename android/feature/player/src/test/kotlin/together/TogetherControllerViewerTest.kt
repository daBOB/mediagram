package player.together

import data.TOGETHER_UNREACHABLE
import data.TogetherLive
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import model.together.PlaybackChange
import model.together.Session
import uniffi.mediagram_core.TogetherEdit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The viewer's own moves reaching the session: once each, a scrub as one edit, and the end of the last title. */
class TogetherControllerViewerTest {
    @Test
    fun aPlayOrPauseIsSentAtOnce() =
        runTest {
            val rig = TogetherRig(this).joined()
            val sent = rig.core.togetherEdits.size

            rig.controller.viewer(PlaybackChange(playing = false))
            runCurrent()

            assertEquals(sent + 1, rig.core.togetherEdits.size)
            assertEquals(false, rig.message(950).playing)
        }

    @Test
    fun aScrubSendsOneEditWithWhereItStopped() =
        runTest {
            val rig = TogetherRig(this).joined()
            val sent = rig.core.togetherEdits.size

            for (pos in listOf(100.0, 200.0, 300.0)) {
                rig.controller.viewer(PlaybackChange(pos = pos))
                advanceTimeBy(100)
            }
            advanceTimeBy(SEEK_SETTLE_MS)
            runCurrent()

            assertEquals(sent + 1, rig.core.togetherEdits.size)
            // Where the scrub stopped, played on for the 300 ms it settled.
            assertEquals(300.3, rig.message(950).pos, 1e-9)
        }

    @Test
    fun aMoveMadeWhileAScrubSettlesCarriesWhereTheScrubWent() =
        runTest {
            val rig = TogetherRig(this).joined()
            val sent = rig.core.togetherEdits.size

            rig.controller.viewer(PlaybackChange(pos = 100.0))
            rig.controller.viewer(PlaybackChange(playing = false))
            advanceTimeBy(SEEK_SETTLE_MS)
            runCurrent()

            assertEquals(sent + 1, rig.core.togetherEdits.size)
            assertEquals(false to 100.0, rig.message(950).let { it.playing to it.pos })
        }

    @Test
    fun aPositionNoPlayerCouldHoldIsNeverWritten() =
        runTest {
            val rig = TogetherRig(this).joined()

            rig.controller.viewer(PlaybackChange(pos = Double.NaN))
            advanceTimeBy(SEEK_SETTLE_MS)
            runCurrent()

            assertTrue(rig.message(950).pos.isFinite())
        }

    @Test
    fun aMoveOutsideASessionSendsNothing() =
        runTest {
            val rig = TogetherRig(this)
            rig.controller.open(EP1)
            runCurrent()

            rig.controller.viewer(PlaybackChange(playing = false))
            runCurrent()

            assertTrue(rig.core.togetherEdits.isEmpty())
        }

    @Test
    fun openingAnotherTitleInASessionMovesTheSessionToItsTop() =
        runTest {
            val rig = TogetherRig(this).joined()

            val start = rig.controller.open(EP2)
            runCurrent()

            val moved = rig.message(950)
            assertEquals(Triple(EP2, 0.0, true), Triple(moved.set, moved.pos, moved.playing))
            assertEquals(0.0, start?.pos)
        }

    @Test
    fun aMoveRefusedByAFloodWaitGoesAgainOnceTheWaitIsOverOverAnEditHeardMeanwhile() =
        runTest {
            val rig = TogetherRig(this).joined()
            rig.core.togetherEditOutcomes += CompletableDeferred(TogetherEdit.Wait(5u))
            rig.controller.viewer(PlaybackChange(playing = false))
            runCurrent()
            val sent = rig.core.togetherEdits.size

            val heard = rig.message(950)
            rig.push(950, heard.copy(members = heard.members + PHONE, at = rig.now(), by = PHONE.screen))
            advanceTimeBy(4_999)
            runCurrent()
            assertEquals(sent, rig.core.togetherEdits.size)
            advanceTimeBy(1)
            runCurrent()

            val landed = rig.message(950)
            assertEquals(sent + 1, rig.core.togetherEdits.size)
            assertEquals(false, landed.playing)
            assertEquals(listOf("TV", "Tablet", "Phone"), landed.members.map { it.label })
        }

    @Test
    fun aMoveWhileTelegramIsOutOfReachSaysSoAndPlaysOn() =
        runTest {
            val rig = TogetherRig(this).joined()
            rig.core.togetherFailure = IllegalStateException("offline")
            rig.live.refresh()

            rig.controller.viewer(PlaybackChange(playing = false))
            runCurrent()

            assertEquals(TOGETHER_UNREACHABLE, rig.controller.ui.value.notice)
            assertEquals(TogetherButton.LEAVE, rig.controller.ui.value.button)
        }

    @Test
    fun anEditTheChannelFindsGoneEndsTheSessionHere() =
        runTest {
            val rig = TogetherRig(this).joined()
            rig.core.togetherMessages.remove(950)

            rig.controller.viewer(PlaybackChange(playing = false))
            runCurrent()

            assertEquals(ENDED, rig.controller.ui.value.notice)
            assertEquals(TogetherButton.START, rig.controller.ui.value.button)
        }

    @Test
    fun aMoveToASessionThisDeviceNoLongerHoldsEndsItHere() =
        runTest {
            val rig =
                TogetherRig(this) { real ->
                    object : TogetherLive by real {
                        override suspend fun act(id: Int, screen: String, change: PlaybackChange): Session? = null
                    }
                }.joined()

            rig.controller.viewer(PlaybackChange(playing = false))
            runCurrent()

            assertEquals(ENDED, rig.controller.ui.value.notice)
            assertEquals(TogetherButton.JOIN, rig.controller.ui.value.button)
        }

    @Test
    fun theEndOfTheLastTitleEndsTheSessionForEveryoneInIt() =
        runTest {
            val rig = TogetherRig(this).joined()

            rig.controller.onTitleEnded(hasNext = false)
            runCurrent()

            assertEquals(true, rig.edits.last().ended)
            assertEquals(listOf(950), rig.core.togetherDeletes)
            assertNull(rig.controller.ui.value.notice)
        }

    @Test
    fun theEndOfAnEpisodeWithAnotherNextLeavesTheMoveToUpNext() =
        runTest {
            val rig = TogetherRig(this).joined()
            val sent = rig.core.togetherEdits.size

            rig.controller.onTitleEnded(hasNext = true)
            runCurrent()

            assertEquals(sent, rig.core.togetherEdits.size)
            assertEquals(TogetherButton.LEAVE, rig.controller.ui.value.button)
        }
}
