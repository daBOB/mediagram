package data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import model.together.PlaybackChange
import model.together.finished
import model.together.joined
import model.together.left
import model.together.started
import uniffi.mediagram_core.TogetherEdit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * This device's moves reaching the channel through the one sender: each
 * re-applied to the newest session known when it goes, kept through a flood
 * wait, and the last one out ending and deleting the message.
 */
class TogetherSenderTest {
    private val pause = PlaybackChange(playing = false, pos = 42.0)
    private val wait = TogetherEdit.Wait(5u)

    @Test
    fun startSendsOneMessageAndASecondStartMeetsTheFirst() =
        runTest {
            val h = TogetherHarness(this)

            val first = h.live.start(A, FILM)

            assertEquals(false, first.conflict)
            assertEquals(started(first.live.session.session, A, FILM, NOW), first.live.session)
            assertEquals(mapOf(first.live.id to first.live.session), h.core.togetherMessages.mapValues { h.message(it.key) })
            assertEquals(first.live, h.live.live.value)

            assertEquals(Started(first.live, conflict = true), h.live.start(TV, FILM.copy(set = "film-2")))
            assertEquals(1, h.core.togetherMessages.size)
        }

    /** The first start waits on a read here; without starts taken one at a time, the second would send a second session. */
    @Test
    fun twoStartsAtOnceTheSecondMeetsTheFirst() =
        runTest {
            val h = TogetherHarness(this)
            h.watch()
            h.push(800, TV_SESSION) // remembered, though its message is gone: each start reads it by id
            val gate = CompletableDeferred<Unit>()
            h.core.togetherReadGate = gate

            val first = async { h.live.start(A, FILM) }
            val second = async { h.live.start(B, FILM) }
            runCurrent()
            gate.complete(Unit)

            assertEquals(false, first.await().conflict)
            assertEquals(Started(first.await().live, conflict = true), second.await())
            assertEquals(1, h.core.togetherMessages.size)
        }

    @Test
    fun aReadOrAJoinWhileAnEditIsOnItsWayBuildsOnThatEditNeverOnTheTextBeforeIt() =
        runTest {
            val h = TogetherHarness(this)
            val id = h.start()
            val held = h.holdEdit()
            val paused = h.live.act(id, A.screen, pause)!!
            runCurrent()

            assertEquals(paused, h.live.refresh()?.session)
            val live = h.live.join(id, B)
            assertEquals(paused.copy(by = B.screen, members = listOf(A, B)), live?.session)

            held.complete(TogetherEdit.Ok)
            runCurrent()
            assertEquals(listOf(paused, live!!.session), h.edits)
            assertEquals(emptyList(), h.core.togetherReads)
        }

    @Test
    fun aJoinAndAMoveQueuedBehindItBothLand() =
        runTest {
            val h = TogetherHarness(this)
            val id = h.start()
            val held = h.holdEdit()
            h.live.act(id, A.screen, PlaybackChange(playing = false))
            runCurrent()
            h.live.join(id, B)
            val sought = h.live.act(id, B.screen, PlaybackChange(pos = 90.0))!!

            held.complete(TogetherEdit.Ok)
            runCurrent()

            assertEquals(2, h.edits.size)
            assertEquals(sought, h.edits[1])
            assertEquals(listOf(false, 90.0, B.screen, listOf(A, B)), listOf(sought.playing, sought.pos, sought.by, sought.members))
        }

    @Test
    fun movesMadeWhileAnEditIsOnItsWayOnlyTheNewestIsSent() =
        runTest {
            val h = TogetherHarness(this)
            val id = h.start()
            val held = h.holdEdit()
            h.live.act(id, A.screen, PlaybackChange(playing = false))
            runCurrent()
            h.live.act(id, A.screen, PlaybackChange(pos = 50.0))
            val last = h.live.act(id, A.screen, PlaybackChange(rate = 2.0))!!

            held.complete(TogetherEdit.Ok)
            runCurrent()

            assertEquals(listOf(false, 50.0, 2.0), listOf(last.playing, last.pos, last.rate))
            assertEquals(2, h.edits.size)
            assertEquals(last, h.edits[1])
        }

    @Test
    fun aMoveMadeWhileAnEditIsRefusedGoesAfterTheWaitNeverSoonerSoTheNewestWins() =
        runTest {
            val h = TogetherHarness(this)
            val id = h.start()
            val refused = h.holdEdit()
            h.live.act(id, A.screen, pause)
            runCurrent()
            val sought = h.live.act(id, A.screen, PlaybackChange(pos = 60.0))!!

            refused.complete(wait)
            runCurrent()
            advanceTimeBy(4_999)
            runCurrent()
            assertEquals(1, h.edits.size)
            advanceTimeBy(1)
            runCurrent()

            assertEquals(listOf(false, 60.0), listOf(sought.playing, sought.pos))
            assertEquals(listOf(sought), h.edits.drop(1))
        }

    @Test
    fun aLateEchoOfAnEarlierOwnEditDoesNotUndoTheOneOnItsWay() =
        runTest {
            val h = TogetherHarness(this)
            h.watch()
            val id = h.start()
            val first = h.live.act(id, A.screen, PlaybackChange(playing = false, pos = 20.0))!!
            runCurrent()
            val held = h.holdEdit()
            val second = h.live.act(id, B.screen, PlaybackChange(pos = 70.0))!!
            runCurrent()

            h.push(id, first)
            val live = h.live.join(id, TV)

            assertEquals(second.copy(by = TV.screen, members = listOf(A, TV)), live?.session)
            held.complete(TogetherEdit.Ok)
            runCurrent()
            assertEquals(listOf(70.0, listOf(A, TV)), h.edits.last().let { listOf(it.pos, it.members) })
        }

    @Test
    fun anotherScreensEditIsNewsButAnOwnEchoWhileANewerWriteWaitsIsNot() =
        runTest {
            val h = TogetherHarness(this)
            h.watch()
            h.push(950, TV_SESSION)
            assertEquals(LiveSession(950, TV_SESSION), h.live.live.value)
            val id = h.start()
            val held = h.holdEdit()
            val first = h.live.act(id, A.screen, PlaybackChange(playing = false))!!
            runCurrent()
            val second = h.live.act(id, A.screen, PlaybackChange(pos = 30.0))!!

            h.push(id, first)
            assertEquals(LiveSession(id, second), h.live.live.value)

            // Another screen's move while the own write still waits is kept, and the own write still goes.
            h.push(id, first.copy(rate = 1.5, by = TV.screen, members = listOf(A, TV)))
            assertEquals(LiveSession(id, second.copy(members = listOf(A, TV))), h.live.live.value)
            held.complete(TogetherEdit.Ok)
            runCurrent()
            assertEquals(second.copy(members = listOf(A, TV)), h.edits[1])
        }

    /** The echo is of a text the channel no longer holds: kept, it would put the TV's join out of what the waiting move goes onto. */
    @Test
    fun aLateOwnEchoHeardWhileAMoveWaitsOutAFloodWaitDoesNotUndoAnotherScreensEdit() =
        runTest {
            val h = TogetherHarness(this)
            h.watch()
            val id = h.start()
            val first = h.live.act(id, A.screen, PlaybackChange(playing = false, pos = 20.0))!!
            runCurrent()
            h.push(id, joined(first, TV))
            h.answerEdit(wait)
            val second = h.live.act(id, A.screen, PlaybackChange(pos = 70.0))!!
            runCurrent()

            h.push(id, first)
            advanceTimeBy(5_000)
            runCurrent()

            assertEquals(listOf(A, TV), second.members)
            assertEquals(second, h.edits.last())
        }

    @Test
    fun aFloodWaitSendsTheNewestOwnActAfterItWithAJoinerHeardMeanwhile() =
        runTest {
            val h = TogetherHarness(this)
            h.watch()
            val id = h.start()
            h.answerEdit(wait)
            val paused = h.live.act(id, A.screen, pause)!!
            runCurrent()

            // The TV still sees the message as started, since the pause was refused.
            h.push(id, joined(h.message(id), TV))
            // This device keeps its own state through the wait, and sees who joined.
            assertEquals(LiveSession(id, paused.copy(members = listOf(A, TV))), h.live.live.value)

            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(listOf(paused.copy(members = listOf(A, TV))), h.edits.drop(1))
        }

    @Test
    fun anotherScreensEditHeardWhileAnEditIsOnItsWaySurvivesThatEditBeingRefused() =
        runTest {
            val h = TogetherHarness(this)
            h.watch()
            val id = h.start()
            val refused = h.holdEdit()
            val paused = h.live.act(id, A.screen, pause)!!
            runCurrent()
            h.push(id, joined(h.message(id), TV))

            refused.complete(wait)
            runCurrent()
            advanceTimeBy(5_000)
            runCurrent()

            assertEquals(paused.copy(members = listOf(A, TV)), h.edits[1])
        }

    @Test
    fun aJoinHeldByAFloodWaitSurvivesAnotherScreensEditMeanwhile() =
        runTest {
            val h = TogetherHarness(this)
            h.watch()
            val id = h.start()
            h.answerEdit(wait)
            h.live.join(id, B)
            runCurrent()

            // The TV pauses the message as it still stands: without the refused join.
            val tvPause = h.message(id).copy(playing = false, by = TV.screen, members = listOf(A, TV))
            h.push(id, tvPause)
            advanceTimeBy(5_000)
            runCurrent()

            assertEquals(tvPause.copy(by = B.screen, members = listOf(A, TV, B)), h.edits[1])
        }

    @Test
    fun aLastLeaveHeldByAFloodWaitRacedByAJoinerLeavesTheSessionToThem() =
        runTest {
            val h = TogetherHarness(this)
            h.watch()
            val id = h.start()
            h.answerEdit(wait)
            h.live.leave(id, A.screen)
            runCurrent()
            assertNull(h.live.live.value)

            val tvJoin = joined(h.message(id), TV)
            h.push(id, tvJoin)
            advanceTimeBy(5_000)
            runCurrent()

            assertEquals(tvJoin.copy(by = A.screen, members = listOf(TV)), h.edits[1])
            assertEquals(emptyList(), h.core.togetherDeletes)
        }

    @Test
    fun anEndHeldByAFloodWaitEndsTheNewestSessionForEveryoneInIt() =
        runTest {
            val h = TogetherHarness(this)
            h.watch()
            val id = h.start()
            h.answerEdit(wait)
            h.live.end(id, A.screen)
            runCurrent()

            val tvJoin = joined(h.message(id), TV)
            h.push(id, tvJoin)
            advanceTimeBy(5_000)
            runCurrent()

            assertEquals(finished(tvJoin, A.screen), h.edits[1])
            assertEquals(listOf(id), h.core.togetherDeletes)
        }

    @Test
    fun aLastLeaveOnItsWayRacedByAJoinerHeardMeanwhileDoesNotDeleteTheMessage() =
        runTest {
            val h = TogetherHarness(this)
            h.watch()
            val id = h.start()
            val held = h.holdEdit()
            h.live.leave(id, A.screen)
            runCurrent()
            h.push(id, joined(h.message(id), TV))

            held.complete(TogetherEdit.Ok)
            runCurrent()

            assertEquals(emptyList(), h.core.togetherDeletes)
        }

    @Test
    fun anOwnWriteWaitingWhenAnotherScreenEndsTheSessionIsDropped() =
        runTest {
            val h = TogetherHarness(this)
            h.watch()
            val id = h.start()
            val held = h.holdEdit()
            val first = h.live.act(id, A.screen, PlaybackChange(playing = false))!!
            runCurrent()
            h.live.act(id, A.screen, PlaybackChange(pos = 30.0))

            h.push(id, first.copy(by = TV.screen, ended = true))
            assertNull(h.live.live.value)
            held.complete(TogetherEdit.Ok)
            runCurrent()

            assertEquals(1, h.edits.size)
        }

    @Test
    fun anEditToADeletedMessageEndsTheSessionHere() =
        runTest {
            val h = TogetherHarness(this)
            val id = h.start()
            h.answerEdit(TogetherEdit.Gone)

            h.live.act(id, A.screen, PlaybackChange(playing = false))
            runCurrent()

            assertNull(h.live.live.value)
            assertNull(h.live.act(id, A.screen, PlaybackChange(playing = true)))
        }

    /** An edit whose call is cancelled under it is no longer on its way, so a re-read of that session goes ahead. */
    @Test
    fun anEditCancelledUnderItIsNoLongerOnItsWay() =
        runTest {
            val h = TogetherHarness(this)
            h.watch()
            val id = h.start()
            h.core.togetherFailure = CancellationException("the core was closed")
            h.live.act(id, A.screen, pause)
            runCurrent()
            h.core.togetherFailure = null

            val tvPlay = h.message(id).copy(by = TV.screen, members = listOf(A, TV))
            h.put(id, tvPlay)
            advanceTimeBy(30_000)
            runCurrent()

            assertEquals(LiveSession(id, tvPlay), h.live.live.value)
        }

    /** Once its message is sent, a session is this device's to keep, even if the screen that asked has gone. */
    @Test
    fun aStartCancelledWhileItsMessageIsSentStillKeepsTheSession() =
        runTest {
            val h = TogetherHarness(this)
            val gate = CompletableDeferred<Unit>()
            h.core.togetherSendGate = gate
            val screen = launch { h.live.start(A, FILM) }
            runCurrent()

            screen.cancel()
            gate.complete(Unit)
            runCurrent()

            val id = h.core.togetherMessages.keys.single()
            assertEquals(LiveSession(id, h.message(id)), h.live.live.value)
        }

    /** What a player that may close mid-call does, as the web's `ask()` does: wait out the answer, then leave if it has gone. */
    @Test
    fun aStartWaitedOutUnderNonCancellableAfterThePlayerClosedCanBeLeft() =
        runTest {
            val h = TogetherHarness(this)
            val gate = CompletableDeferred<Unit>()
            h.core.togetherSendGate = gate
            var closed = false
            val screen =
                launch {
                    withContext(NonCancellable) {
                        val started = h.live.start(A, FILM)
                        if (closed) h.live.leave(started.live.id, A.screen)
                    }
                }
            runCurrent()

            closed = true
            screen.cancel()
            gate.complete(Unit)
            runCurrent()

            assertTrue(h.edits.single().ended)
            assertEquals(emptyMap(), h.core.togetherMessages)
            assertNull(h.live.live.value)
        }

    /**
     * The leave is the device's, not the player's: it needs no scope of the
     * caller's, so a player whose own scope is already cancelled, as it is by
     * the time `onCleared` runs, still leaves.
     */
    @Test
    fun theLastToLeaveWritesTheEndAndDeletesTheMessage() =
        runTest {
            val h = TogetherHarness(this)
            val id = h.start()
            val begun = h.message(id)

            h.live.leave(id, A.screen)
            runCurrent()

            assertEquals(listOf(left(begun, A.screen)), h.edits)
            assertTrue(h.edits.single().let { it.ended && it.members.isEmpty() })
            assertEquals(listOf(id), h.core.togetherDeletes)
            assertNull(h.live.live.value)
        }

    @Test
    fun aLastLeaveRefusedByAFloodWaitIsWrittenAndDeletedOnceTheWaitEnds() =
        runTest { refusedLastMoveIsWrittenAndDeleted { leave(it, A.screen) } }

    @Test
    fun aLastEndRefusedByAFloodWaitIsWrittenAndDeletedOnceTheWaitEnds() =
        runTest { refusedLastMoveIsWrittenAndDeleted { end(it, A.screen) } }

    @Test
    fun aReadDuringALastLeaveOnItsWayLeavesTheMessageToTheSender() =
        runTest { readDuringALastMoveLeavesTheMessageToTheSender { leave(it, A.screen) } }

    @Test
    fun aReadDuringALastEndOnItsWayLeavesTheMessageToTheSender() =
        runTest { readDuringALastMoveLeavesTheMessageToTheSender { end(it, A.screen) } }

    @Test
    fun theEndOfTheLastTitleEndsItForEveryoneStillInIt() =
        runTest {
            val h = TogetherHarness(this)
            h.watch()
            val id = h.start()
            h.push(id, joined(h.message(id), TV))

            h.live.end(id, A.screen)
            runCurrent()

            assertEquals(listOf(listOf(A, TV)), h.edits.map { it.members })
            assertTrue(h.edits.single().let { it.ended && !it.playing })
            assertEquals(listOf(id), h.core.togetherDeletes)
            assertNull(h.live.live.value)
        }

    @Test
    fun joinAddsThisScreenToASessionItReadAndADeletedOrStaleOneCannotBeJoined() =
        runTest {
            val h = TogetherHarness(this)
            h.put(950, TV_SESSION)
            h.put(951, TV_SESSION.copy(session = "s-old", playing = false, at = NOW - 4 * HOUR_MS))

            val live = h.live.join(950, A)
            runCurrent()

            assertEquals(LiveSession(950, TV_SESSION.copy(by = A.screen, members = listOf(TV, A))), live)
            assertEquals(listOf(live!!.session), h.edits)
            assertNull(h.live.join(999, A))
            assertNull(h.live.join(951, A))
        }

    /** Every screen parses what this one writes: a position or speed no parser accepts would end the session for all of them. */
    @Test
    fun aPositionOrSpeedNoScreenCouldReadIsNeverWritten() =
        runTest {
            val h = TogetherHarness(this)
            val begun = h.live.start(A, FILM.copy(pos = Double.NaN, rate = 0.0)).live
            assertEquals(listOf(0.0, 1.0), listOf(begun.session.pos, begun.session.rate))
            assertEquals(begun.session, h.message(begun.id))

            val moved = h.live.act(begun.id, A.screen, PlaybackChange(pos = Double.POSITIVE_INFINITY, rate = -1.0))!!
            runCurrent()

            assertEquals(listOf(0.0, 1.0), listOf(moved.pos, moved.rate))
            assertEquals(moved, h.message(begun.id))
        }

    @Test
    fun aJoinBuildsOnAnEditHeardWhileItsReadWasOnItsWay() =
        runTest {
            val h = TogetherHarness(this)
            h.watch()
            h.put(950, TV_SESSION)
            val gate = CompletableDeferred<Unit>()
            h.core.togetherReadGate = gate

            val live = async { h.live.join(950, A) }
            runCurrent()
            val tvPause = TV_SESSION.copy(playing = false, pos = 30.0)
            h.push(950, tvPause)
            gate.complete(Unit)

            assertEquals(LiveSession(950, joined(tvPause, A)), live.await())
        }

    private suspend fun TestScope.refusedLastMoveIsWrittenAndDeleted(move: TogetherLive.(Int) -> Unit) {
        val h = TogetherHarness(this)
        val id = h.start()
        h.answerEdit(wait)

        h.live.move(id)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(listOf(true, true), h.edits.map { it.ended })
        assertEquals(listOf(id), h.core.togetherDeletes)
    }

    private suspend fun TestScope.readDuringALastMoveLeavesTheMessageToTheSender(move: TogetherLive.(Int) -> Unit) {
        val h = TogetherHarness(this)
        val id = h.start()
        val held = h.holdEdit()
        h.live.move(id)
        runCurrent()

        assertNull(h.live.refresh())
        runCurrent()
        assertEquals(emptyList(), h.core.togetherDeletes)

        held.complete(TogetherEdit.Ok)
        runCurrent()
        assertEquals(listOf(true), h.edits.map { it.ended })
        assertEquals(listOf(id), h.core.togetherDeletes)
    }
}
